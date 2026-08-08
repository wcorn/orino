# 여행 지도(Maps JavaScript API) — #1102.
#
# 왜 구글 지도를 쓰게 됐나: 약관의 "No Use With Non-Google Maps"가 Places·Routes 콘텐츠를
# 비구글 지도와 "with or near" 쓰는 것을 금지한다. 일정 상세는 주소·영업시간이, 지도 화면은
# 이동시간이 지도 옆에 붙어 있어 지도 자체를 바꾸는 것 말고는 방법이 없었다.
#
# 이 키는 gcp-travel.tf의 서버 키와 **성격이 다르다**.
#
# | | 서버 키(Places/Routes) | 이 키(Maps JS) |
# |---|---|---|
# | 어디서 쓰나 | BE가 자기 이름으로 | 브라우저가 |
# | 어떻게 지키나 | API 제한(IP 제한은 D-15로 포기) | **HTTP 리퍼러 제한** |
# | 어디에 담기나 | SealedSecret → 파드 env | **FE 번들에 그대로 들어간다** |
#
# 번들에 들어간다는 것은 누구나 볼 수 있다는 뜻이다. 그래서 이 키는 비밀이 아니고,
# 리퍼러 제한이 유일한 방어선이다 — 서버 키와 절대 섞어 쓰면 안 된다.

locals {
  # Maps JavaScript API의 서비스 이름은 'maps-backend.googleapis.com'이다.
  # 'maps.googleapis.com'이 아니다(그건 존재하지 않는다).
  gcp_maps_service = "maps-backend.googleapis.com"

  # 리퍼러 제한. 이 목록 밖에서 키를 쓰면 구글이 거부한다(gm_authFailure).
  gcp_maps_referrers = [
    "https://orino.dev/*",
    "https://www.orino.dev/*",
    # 로컬 확인용. 지도가 안 뜨는 이유를 로컬에서 재현할 수 있어야 한다.
    "http://localhost:*/*",
  ]
}

resource "google_project_service" "maps_js" {
  project = var.gcp_project_id
  service = local.gcp_maps_service

  # 서버 키와 같은 이유 — 리소스를 지운다고 API까지 끄면 운영 중인 호출이 즉시 죽는다.
  disable_on_destroy = false
}

resource "google_apikeys_key" "travel_maps_js" {
  project = var.gcp_project_id

  # 서버 키와 달리 이 키는 처음부터 Terraform이 만든다(import 대상이 아니다).
  # name은 키 ID다 — 바꾸면 삭제·재생성되고 키 문자열이 바뀐다.
  name         = "orino-travel-maps-js"
  display_name = "orino-travel-maps-js"

  restrictions {
    browser_key_restrictions {
      allowed_referrers = local.gcp_maps_referrers
    }

    # 이 키로 부를 수 있는 것은 지도 하나뿐이다. 새면 지도 로드만 도둑맞고,
    # Places·Routes(건당 과금이 큰 쪽)는 이 키로 못 부른다.
    api_targets {
      service = local.gcp_maps_service
    }
  }

  depends_on = [google_project_service.maps_js]
}

# 키 문자열은 상태 파일에 들어간다. 출력으로 꺼내지 않는 이유는 CI 로그에 남기지 않기
# 위해서다 — 값이 필요하면 아래로 읽는다.
#
#   gcloud services api-keys get-key-string \
#     projects/202935442863/locations/global/keys/orino-travel-maps-js
#
# 읽은 값은 .secrets에 기록하고 GitHub Actions 시크릿(VITE_GOOGLE_MAPS_API_KEY)에 넣는다.
# SealedSecret이 아니다 — 이 키는 클러스터가 아니라 **FE 빌드**가 쓴다.
output "gcp_maps_js_key_name" {
  description = "여행 지도 브라우저 키 리소스 이름 (값은 gcloud로 읽어 .secrets 참조)"
  value       = google_apikeys_key.travel_maps_js.name
}

# ---------------------------------------------------------------------------
# Map ID는 Terraform으로 못 만든다.
#
# Advanced Marker에 Map ID가 필요한데, google provider에 해당 리소스가 없다
# (2026-08 기준. google_compute_url_map·certificate_manager_certificate_map 같은
# 이름만 비슷한 것들뿐이다). 결제 계정 카드 등록과 같은 부류의 콘솔 작업이다.
#
#   콘솔 → Google Maps Platform → Map Management → Create Map ID
#   - Map type: JavaScript
#   - 스타일은 지정하지 않아도 된다(기본 지도로 뜬다)
#
# 만든 값은 .secrets에 기록하고 GitHub Actions 시크릿(VITE_GOOGLE_MAPS_MAP_ID)에 넣는다.
# 없으면 지도는 뜨지만 **마커가 하나도 안 뜬다** — 조용히 실패하므로 잊으면 찾기 어렵다.
# ---------------------------------------------------------------------------
