# 여행 모듈이 쓰는 Google Cloud 리소스 (#1050, Epic #1029 · 2단계).
#
# Places = 장소 검색·상세, Routes = 이동시간(WALK/DRIVE). 둘 다 유료 API라
# 결제 계정 연결이 전제다(콘솔에서 카드 등록 — 코드로 못 한다).
#
# 이 파일의 리소스는 처음에 gcloud로 만들어졌다. import 블록으로 그대로 넘겨받아
# 재생성 없이 Terraform 관리로 옮긴다(#1052).

locals {
  gcp_travel_services = [
    "places.googleapis.com",
    "routes.googleapis.com",
    "billingbudgets.googleapis.com",
  ]
}

resource "google_project_service" "travel" {
  for_each = toset(local.gcp_travel_services)

  project = var.gcp_project_id
  service = each.value

  # 이 리소스를 지운다고 API까지 끄지 않는다 — 끄면 운영 중인 호출이 즉시 죽는다.
  disable_on_destroy = false
}

# 서버가 자기 이름으로 호출하는 API 키. 사용자 OAuth(google-secret)와 별개다.
#
# IP 제한을 두지 않는 이유는 결정 기록 D-15 — 클러스터 이그레스가 집 회선이라
# 공인 IP가 고정이 아니다. 대신 호출 가능한 API를 둘로 묶고, 금전 피해는 예산 알림이 잡는다.
resource "google_apikeys_key" "travel_places_routes" {
  project      = var.gcp_project_id
  name         = "orino-travel-places-routes"
  display_name = "orino-travel-places-routes"

  restrictions {
    api_targets {
      service = "places.googleapis.com"
    }
    api_targets {
      service = "routes.googleapis.com"
    }
  }

  depends_on = [google_project_service.travel]
}

# 프로젝트 전체 월 예산. Places/Routes만 걸면 다른 API가 폭주할 때 못 잡는다.
#
# 1인 사용이라 정상 사용은 무료 한도 안에서 끝난다. 이 금액은 "정상이면 절대 안 닿는" 선이면서
# 키가 새어 폭주할 때 카드가 크게 긁히기 전에 걸리는 값이다.
resource "google_billing_budget" "orino" {
  billing_account = var.gcp_billing_account
  display_name    = "orino 월 예산"

  budget_filter {
    projects = ["projects/${var.gcp_project_number}"]
  }

  amount {
    specified_amount {
      currency_code = "KRW"
      units         = tostring(var.gcp_budget_krw)
    }
  }

  threshold_rules {
    threshold_percent = 0.5
  }
  threshold_rules {
    threshold_percent = 0.9
  }
  threshold_rules {
    threshold_percent = 1.0
  }
  # 실제로 닿기 전에 미리 알리는 조기 경보.
  threshold_rules {
    threshold_percent = 1.0
    spend_basis       = "FORECASTED_SPEND"
  }

  depends_on = [google_project_service.travel]
}

output "gcp_travel_api_key_name" {
  description = "여행 Places/Routes API 키 리소스 이름 (값은 .secrets / SealedSecret 참조)"
  value       = google_apikeys_key.travel_places_routes.name
}
