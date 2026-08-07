# 이미 존재하는 GCP 리소스를 Terraform state로 넘겨받는다 (#1052).
#
# 이 리소스들은 2단계 착수를 서두르다 gcloud로 먼저 만들어졌다. import 블록을 쓰면
# 삭제·재생성 없이 state에만 편입된다 — API 키가 새로 발급되면 SealedSecret과
# .secrets를 다시 봉인해야 하므로 재생성은 피해야 한다.
#
# 적용 후(= state에 들어온 뒤) 이 파일은 지워도 된다. 남겨 두면 매 plan마다
# 이미 import된 리소스를 다시 확인할 뿐 동작에는 영향이 없다.

import {
  to = google_project_service.travel["places.googleapis.com"]
  id = "orino-499511/places.googleapis.com"
}

import {
  to = google_project_service.travel["routes.googleapis.com"]
  id = "orino-499511/routes.googleapis.com"
}

import {
  to = google_project_service.travel["billingbudgets.googleapis.com"]
  id = "orino-499511/billingbudgets.googleapis.com"
}

import {
  to = google_apikeys_key.travel_places_routes
  id = "projects/orino-499511/locations/global/keys/18229d57-dd99-439e-82a7-41ee91af8308"
}

import {
  to = google_billing_budget.orino
  id = "billingAccounts/01EDAC-CEFAA5-004180/budgets/09bb17e9-6ac6-4c7d-8f3c-4a7d8eeae375"
}

# --- CI 자격증명 (#1052) ---
# 풀·provider는 콘솔에서, 서비스 계정·바인딩은 CLI로 만들어졌다. 여기서 넘겨받는다.

import {
  to = google_iam_workload_identity_pool.github
  id = "projects/orino-499511/locations/global/workloadIdentityPools/github-actions"
}

import {
  to = google_iam_workload_identity_pool_provider.github
  id = "projects/orino-499511/locations/global/workloadIdentityPools/github-actions/providers/github"
}

import {
  to = google_service_account.terraform_apply
  id = "projects/orino-499511/serviceAccounts/github-actions-terraform@orino-499511.iam.gserviceaccount.com"
}

import {
  to = google_service_account.terraform_plan
  id = "projects/orino-499511/serviceAccounts/github-actions-terraform-plan@orino-499511.iam.gserviceaccount.com"
}

import {
  to = google_project_iam_member.terraform_apply["roles/serviceusage.serviceUsageAdmin"]
  id = "orino-499511 roles/serviceusage.serviceUsageAdmin serviceAccount:github-actions-terraform@orino-499511.iam.gserviceaccount.com"
}

import {
  to = google_project_iam_member.terraform_apply["roles/serviceusage.apiKeysAdmin"]
  id = "orino-499511 roles/serviceusage.apiKeysAdmin serviceAccount:github-actions-terraform@orino-499511.iam.gserviceaccount.com"
}

import {
  to = google_project_iam_member.terraform_plan["roles/serviceusage.serviceUsageViewer"]
  id = "orino-499511 roles/serviceusage.serviceUsageViewer serviceAccount:github-actions-terraform-plan@orino-499511.iam.gserviceaccount.com"
}

import {
  to = google_project_iam_member.terraform_plan["roles/serviceusage.apiKeysViewer"]
  id = "orino-499511 roles/serviceusage.apiKeysViewer serviceAccount:github-actions-terraform-plan@orino-499511.iam.gserviceaccount.com"
}

import {
  to = google_billing_account_iam_member.terraform_apply
  id = "billingAccounts/01EDAC-CEFAA5-004180 roles/billing.costsManager serviceAccount:github-actions-terraform@orino-499511.iam.gserviceaccount.com"
}

import {
  to = google_billing_account_iam_member.terraform_plan
  id = "billingAccounts/01EDAC-CEFAA5-004180 roles/billing.viewer serviceAccount:github-actions-terraform-plan@orino-499511.iam.gserviceaccount.com"
}

import {
  to = google_service_account_iam_member.terraform_apply_wif
  id = "projects/orino-499511/serviceAccounts/github-actions-terraform@orino-499511.iam.gserviceaccount.com roles/iam.workloadIdentityUser principalSet://iam.googleapis.com/projects/202935442863/locations/global/workloadIdentityPools/github-actions/attribute.repository/wcorn/orino"
}

import {
  to = google_service_account_iam_member.terraform_plan_wif
  id = "projects/orino-499511/serviceAccounts/github-actions-terraform-plan@orino-499511.iam.gserviceaccount.com roles/iam.workloadIdentityUser principalSet://iam.googleapis.com/projects/202935442863/locations/global/workloadIdentityPools/github-actions/attribute.repository/wcorn/orino"
}
