# GitHub Actions → GCP — 정적 키 없이 Workload Identity Federation 으로 terraform 실행.
# AWS 쪽(cicd.tf)과 같은 구조다:
#   PR(untrusted)       → terraform-plan  SA (읽기 전용)
#   main 머지(trusted)  → terraform-apply SA (쓰기 가능)
#
# 이 자격증명들은 "자기 자신을 만드는" 관계라 처음 한 번은 코드 밖에서 만들 수밖에 없다
# (풀·provider는 콘솔, 서비스 계정은 CLI). 만든 뒤 import로 넘겨받아 이후로는 코드가 관리한다.
# AWS의 OIDC provider·role도 같은 경로를 거쳤다.

resource "google_iam_workload_identity_pool" "github" {
  project                   = var.gcp_project_id
  workload_identity_pool_id = "github-actions"
  display_name              = "github-actions"
}

resource "google_iam_workload_identity_pool_provider" "github" {
  project                            = var.gcp_project_id
  workload_identity_pool_id          = google_iam_workload_identity_pool.github.workload_identity_pool_id
  workload_identity_pool_provider_id = "github"

  attribute_mapping = {
    "google.subject"       = "assertion.sub"
    "attribute.repository" = "assertion.repository"
  }

  # 이 조건이 없으면 GitHub의 아무 리포지토리나 이 SA를 가장할 수 있다.
  # WIF에서 가장 흔한 사고라 반드시 둔다.
  attribute_condition = "assertion.repository == 'wcorn/orino'"

  oidc {
    issuer_uri = "https://token.actions.githubusercontent.com"
  }
}

# ---------------------------------------------------------------------------
# apply SA — main 머지(trusted). 쓰기 권한.
# ---------------------------------------------------------------------------
resource "google_service_account" "terraform_apply" {
  project      = var.gcp_project_id
  account_id   = "github-actions-terraform"
  display_name = "Terraform apply (main merge)"
}

resource "google_project_iam_member" "terraform_apply" {
  # Terraform은 자기가 만드는 리소스보다 넓은 권한이 필요하다 — refresh 때 IAM 정책·
  # 서비스 계정·WIF 풀을 읽어야 하고, 자기 자신(cicd)의 바인딩도 관리하기 때문이다.
  for_each = toset([
    "roles/serviceusage.serviceUsageAdmin",  # API 활성화/비활성화
    "roles/serviceusage.apiKeysAdmin",       # API 키 생성·제한 변경
    "roles/iam.workloadIdentityPoolAdmin",   # WIF 풀·provider
    "roles/iam.serviceAccountAdmin",         # 서비스 계정과 그 IAM 정책
    "roles/resourcemanager.projectIamAdmin", # 프로젝트 역할 바인딩
  ])

  project = var.gcp_project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.terraform_apply.email}"
}

# ---------------------------------------------------------------------------
# plan SA — PR(untrusted). 읽기 전용이라 포크 PR도 리소스를 못 바꾼다.
# ---------------------------------------------------------------------------
resource "google_service_account" "terraform_plan" {
  project      = var.gcp_project_id
  account_id   = "github-actions-terraform-plan"
  display_name = "Terraform plan (PR, read-only)"
}

resource "google_project_iam_member" "terraform_plan" {
  for_each = toset([
    "roles/serviceusage.serviceUsageViewer",
    # plan이 API 키를 refresh 하려면 키 문자열 조회까지 필요하다(apikeys.keys.getKeyString).
    "roles/serviceusage.apiKeysViewer",
    "roles/iam.workloadIdentityPoolViewer",
    # 서비스 계정과 프로젝트 IAM 정책 읽기. 이게 없으면 plan이 403으로 깨진다.
    "roles/iam.securityReviewer",
  ])

  project = var.gcp_project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.terraform_plan.email}"
}

# ---------------------------------------------------------------------------
# 결제 계정 역할 — 예산은 프로젝트가 아니라 결제 계정에 붙는 리소스다.
# 프로젝트에 부여하면 예산을 못 읽고/못 만든다.
# ---------------------------------------------------------------------------
resource "google_billing_account_iam_member" "terraform_apply" {
  billing_account_id = var.gcp_billing_account
  role               = "roles/billing.costsManager"
  member             = "serviceAccount:${google_service_account.terraform_apply.email}"
}

resource "google_billing_account_iam_member" "terraform_plan" {
  billing_account_id = var.gcp_billing_account
  role               = "roles/billing.viewer"
  member             = "serviceAccount:${google_service_account.terraform_plan.email}"
}

# ---------------------------------------------------------------------------
# WIF 바인딩 — wcorn/orino 워크플로만 이 SA를 가장할 수 있다.
# ---------------------------------------------------------------------------
locals {
  gcp_wif_principal = "principalSet://iam.googleapis.com/projects/${var.gcp_project_number}/locations/global/workloadIdentityPools/${google_iam_workload_identity_pool.github.workload_identity_pool_id}/attribute.repository/wcorn/orino"
}

resource "google_service_account_iam_member" "terraform_apply_wif" {
  service_account_id = google_service_account.terraform_apply.name
  role               = "roles/iam.workloadIdentityUser"
  member             = local.gcp_wif_principal
}

resource "google_service_account_iam_member" "terraform_plan_wif" {
  service_account_id = google_service_account.terraform_plan.name
  role               = "roles/iam.workloadIdentityUser"
  member             = local.gcp_wif_principal
}

output "gcp_terraform_apply_sa" {
  description = "main 머지 시 apply에 사용하는 서비스 계정"
  value       = google_service_account.terraform_apply.email
}

output "gcp_terraform_plan_sa" {
  description = "PR plan(읽기 전용)에 사용하는 서비스 계정"
  value       = google_service_account.terraform_plan.email
}
