# GCP 메서드별 일일 할당량 하드캡 (#1151 · Epic #1148).
#
# 예산 알림은 지출을 멈추지 않는다. 이건 멈춘다.
# 10/24 도쿄에서는 시차·로밍·이동 때문에 알림을 받고도 대응할 수 없는 시간대가 있다.
# 일일 할당량 override 는 무료이고, Google 이 서버 측에서 집행하고, 자는 동안에도 작동한다.
#
# ── 캡은 API 가 아니라 메서드 단위로만 걸린다 ────────────────────────────────
# `1/d/{project}` 단위가 붙은 메트릭에만 override 를 걸 수 있다. gcloud 로 실측해
# 확인했다(2026-08-11). limit 은 그 단위를 URL 인코딩한 "%2Fd%2Fproject" 이고,
# metric 이름도 "/" 를 인코딩해야 한다 — plan 이 이상하면 여기부터 본다.
#
# ── force = true 가 필요한 이유 ──────────────────────────────────────────────
# 기존 한도를 10% 넘게 깎으면 API 가 요청을 거부한다. 75,000 -> 500 은 99% 감소라
# force 없이는 apply 가 깨진다.
#
# ── 지금은 넉넉하게 건다 ─────────────────────────────────────────────────────
# ⚠️ 캡에 걸리면 앱은 429/403 을 받는다. 여행 중 앱이 죽으면 안 된다. 여기서는
# 30일 실측의 100배 이상 여유를 둔 값만 걸고, 실제로 조이는 것은 #1160 에서
# graceful degradation(#1159)과 예상 청구 산출을 마친 뒤에 한다.

locals {
  # 앱이 실제로 부르는 메서드. 30일 실측(Places 96 · Routes 21 · Maps JS 14)의
  # 100배 이상 여유를 둔 값이다.
  #
  # GetPhotoMediaRequest 가 이 목록의 숨은 핵심이다. 지금 사용량은 0 이지만,
  # ELOG-023 이 "같은 함정의 가장 큰 버전"이라고 부른 것 — 검색 결과 20개를
  # 그리면서 사진을 매번 받으면 화면당 20 유료 호출 — 을 계측(#1158)은 사후에
  # 알려주고 이 캡은 사전에 막는다.
  gcp_quota_caps_used = {
    places_search_text = {
      service = "places.googleapis.com"
      metric  = "places.googleapis.com/SearchTextRequest"
      value   = "500" # 기본 75,000
    }
    places_get_place = {
      service = "places.googleapis.com"
      metric  = "places.googleapis.com/GetPlaceRequest"
      value   = "500" # 기본 125,000
    }
    places_get_photo = {
      service = "places.googleapis.com"
      metric  = "places.googleapis.com/GetPhotoMediaRequest"
      value   = "200" # 기본 175,000, 실측 0
    }
    routes_compute = {
      service = "routes.googleapis.com"
      metric  = "routes.googleapis.com/compute_routes_requests"
      value   = "200" # 기본 무제한
    }
    maps_js = {
      service = "maps-backend.googleapis.com"
      metric  = "maps-backend.googleapis.com/billable_default"
      value   = "1000" # 기본 무제한
    }
  }

  # 앱이 부르지 않지만 켜져 있어서 부를 수 있는 유료 메서드.
  #
  # 이 이슈의 위협 모델은 "키 유출"이다 — Maps JS 브라우저 키는 FE 번들에 그대로
  # 들어가 있고 리퍼러 제한은 서버 간 호출에서 위조 가능하다. 그런데 유출된 키를
  # 쥔 쪽은 우리가 쓰는 메서드만 골라 부를 이유가 없다. 쓰는 것만 막고 나머지를
  # 열어 두면 캡을 우회하는 문이 그대로 남는다.
  #
  # compute_route_matrix_elements 가 특히 위험하다 — 요청이 아니라 **원소(출발지 ×
  # 도착지) 단위로 과금**되므로 한 번의 호출로 수천 건이 청구될 수 있는데 기본이
  # 무제한이다. computeRoutes 를 200 으로 조이면서 이쪽을 열어 두는 건 앞문만
  # 잠그는 것이다.
  #
  # 값을 0 이 아니라 100 으로 두는 이유: 나중에 이 메서드를 쓰는 기능을 붙일 때
  # 0 이면 개발 첫 호출부터 막혀 원인을 찾느라 헤맨다. 100 이면 개발은 되고
  # 폭주는 막힌다. 실제로 쓰기 시작하면 그때 위 목록으로 옮긴다.
  gcp_quota_caps_unused = {
    places_autocomplete = {
      service = "places.googleapis.com"
      metric  = "places.googleapis.com/AutocompletePlacesRequest"
      value   = "100" # 기본 175,000
    }
    places_search_nearby = {
      service = "places.googleapis.com"
      metric  = "places.googleapis.com/SearchNearbyRequest"
      value   = "100" # 기본 75,000
    }
    places_search_media = {
      service = "places.googleapis.com"
      metric  = "places.googleapis.com/SearchMediaRequest"
      value   = "100" # 기본 무제한
    }
    places_search_review_posts = {
      service = "places.googleapis.com"
      metric  = "places.googleapis.com/SearchReviewPostsRequest"
      value   = "100" # 기본 무제한
    }
    routes_matrix_elements = {
      service = "routes.googleapis.com"
      metric  = "routes.googleapis.com/compute_route_matrix_elements"
      value   = "100" # 기본 무제한. 원소 단위 과금이라 가장 위험하다
    }
    maps_js_3d = {
      service = "maps-backend.googleapis.com"
      metric  = "maps-backend.googleapis.com/3d_billable_default"
      value   = "100" # 기본 무제한
    }
  }

  gcp_quota_caps = merge(local.gcp_quota_caps_used, local.gcp_quota_caps_unused)
}

resource "google_service_usage_consumer_quota_override" "daily" {
  # 이 리소스는 GA provider 에 없다 — google-beta 전용이다(providers.tf 주석 참고).
  provider = google-beta

  for_each = local.gcp_quota_caps

  project        = var.gcp_project_id
  service        = each.value.service
  metric         = urlencode(each.value.metric)
  limit          = urlencode("/d/project")
  override_value = each.value.value

  # 기존 한도를 10% 넘게 깎는 override 는 force 없이는 거부된다.
  force = true

  # places·routes 는 이 리소스가 활성화한다. 활성화 전에 override 를 걸면 깨진다.
  # (maps-backend 는 Terraform 관리 밖이지만 이미 활성 상태다 — gcloud 로 확인)
  depends_on = [google_project_service.travel]
}
