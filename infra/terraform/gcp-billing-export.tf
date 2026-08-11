# GCP 결제 데이터 BigQuery 내보내기 (#1157 · Epic #1148 · P3).
#
# 여행 모듈이 쓰는 Places·Routes·Maps JS 는 호출당 과금이고, 응답 필드마스크에 따라
# SKU 등급이 갈린다(ELOG-023). 지금까지는 개발 트래픽뿐이라(30일간 Places 96 ·
# Routes 21 · Maps JS 14회) **어떤 SKU 가 실제로 얼마에 잡히는지 확인된 적이 없다.**
# #1160 의 "여행 1회 = 예상 ₩N" 산출은 추정 단가가 아니라 이 데이터로만 계산한다.
#
# ── 왜 지금 켜는가 ──────────────────────────────────────────────────────────
# 결제 콘솔에서도 SKU 별 이력을 보고 CSV 로 받을 수 있다. 그러니 "안 하면 영원히
# 못 본다"는 말은 틀렸다. 그럼에도 지금 여는 이유는 다르다:
#   · 콘솔은 사람이 열어봐야 한다 — 자동 알림·대시보드 연동이 안 된다
#   · 라벨·일자별 세분 분석과 쿼리가 안 된다
#   · **상세 사용량 내보내기 데이터는 소급 생성되지 않는다** — 설정한 시점부터만
#     쌓인다. 10/24 실사용 이후에 켜면 그 기간은 영원히 세밀한 분석에서 빠진다
#
# ── 비용 주의 ───────────────────────────────────────────────────────────────
# BigQuery 는 저장 + 쿼리 스캔이 과금된다. 이 규모(월 수백 행)에서는 무료 한도
# 안이지만, 습관적으로 SELECT * 를 돌리면 의미 없이 스캔이 쌓인다. 조회는 항상
# 날짜 파티션을 지정한다. 관측이 과금을 만드는 함정을 이 프로젝트는 두 번 겪었다.
#
# CI 서비스 계정에는 일부러 bigquery.jobs.create 를 주지 않았다(#1186) — CI 는
# 데이터셋을 만들 뿐 쿼리를 돌릴 수 없다.

resource "google_project_service" "bigquery" {
  project = var.gcp_project_id
  service = "bigquery.googleapis.com"

  # 이 리소스를 지운다고 API 까지 끄지 않는다 — 끄면 쌓인 결제 데이터를 못 읽는다.
  disable_on_destroy = false
}

resource "google_bigquery_dataset" "billing_export" {
  project    = var.gcp_project_id
  dataset_id = "billing_export"

  friendly_name = "Cloud Billing 상세 사용량 내보내기"
  description   = "결제 계정 상세 사용량 내보내기 대상. SKU별 실단가 확인용(#1157). 내보내기 활성화 자체는 결제 콘솔에서 한다 — Terraform 리소스가 없다."

  # 단일 리전. 멀티 리전(US/EU)은 이 프로젝트에 필요 없는 복제 비용을 만든다.
  # 결제 내보내기는 데이터셋 위치를 따라가므로 나중에 바꾸려면 재생성이 필요하다.
  location = "asia-northeast3"

  # 파티션 만료 400일 = 13개월. 전년 동월 대비를 볼 수 있는 최소치이면서
  # 무한 증가는 막는다. 이 규모에서는 저장 무료 한도 안이라 절감이 목적이 아니라
  # "지우는 규칙이 선언되어 있고 실제로 도는가"를 지키는 것이 목적이다
  # — 선언만 하고 5개월간 집행되지 않았던 Loki retention 이 ELOG-027 이다.
  default_partition_expiration_ms = 34560000000

  # 실수로 terraform destroy 를 돌려도 쌓인 결제 데이터가 같이 날아가지 않게 한다.
  # 소급 생성되지 않는 데이터라 한 번 잃으면 복구할 방법이 없다.
  delete_contents_on_destroy = false

  depends_on = [google_project_service.bigquery]
}

# ---------------------------------------------------------------------------
# 여기서부터는 코드 밖이다 — 결제 내보내기 활성화에는 Terraform 리소스가 없다.
#
# Cloud Billing 의 BigQuery 내보내기 설정은 Cloud Billing API 로만 바뀌고
# google provider 에 대응 리소스가 없다(google-beta 에도 없다). gcp-cicd.tf 의
# 결제 역할 부트스트랩과 같은 이유로 콘솔 수동 설정이며, 그래서 여기 절차를 남긴다.
#
#   결제 → 청구 계정(01EDAC-CEFAA5-004180) → 결제 내보내기 → BigQuery 내보내기
#     · **상세 사용량 비용 데이터** 를 켠다 (표준 사용량이 아니다 —
#       표준에는 SKU 별 세부·라벨이 없어서 #1160 산출에 못 쓴다)
#     · 프로젝트: orino-499511
#     · 데이터셋: billing_export (이 파일이 만든 것)
#
# 활성화하면 아래 테이블이 자동 생성된다(하이픈은 언더스코어로 치환된다):
#   gcp_billing_export_resource_v1_01EDAC_CEFAA5_004180
#
# ⚠️ 설정 직후에는 테이블이 없다. 첫 데이터가 들어오기까지 최대 하루 걸린다 —
# 다음 날 행이 실제로 쌓였는지 확인한다. 설정이 아니라 집행을 본다.
# ---------------------------------------------------------------------------

output "billing_export_dataset" {
  description = "결제 내보내기 대상 데이터셋 (결제 콘솔에서 이 이름을 고른다)"
  value       = "${var.gcp_project_id}:${google_bigquery_dataset.billing_export.dataset_id}"
}
