variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

# --- 비용 알림 (#1150, Epic #1148) ---

# 기본값을 두지 않는다 — 개인 이메일이고 이 저장소는 공개다. CI 는 워크플로우에서
# TF_VAR_budget_alert_emails 로 주입하고(BUDGET_ALERT_EMAIL 시크릿), 로컬에서는
# terraform.tfvars 에 넣는다.
variable "budget_alert_emails" {
  description = "예산·이상탐지 알림 수신 주소. AWS Budgets 는 이메일 구독자를 직접 지원해 SNS 를 두지 않는다"
  type        = list(string)

  # 시크릿 미설정 시 TF_VAR 가 [""] 로 들어온다. 그대로 두면 AWS API 가 뱉는
  # 알아보기 힘든 에러로 apply 가 깨지므로 원인을 이름으로 알려준다.
  validation {
    condition = length(var.budget_alert_emails) > 0 && alltrue([
      for e in var.budget_alert_emails : can(regex("^[^@[:space:]]+@[^@[:space:]]+\\.[^@[:space:]]+$", e))
    ])
    error_message = "유효한 이메일이 최소 1개 필요하다. CI 라면 BUDGET_ALERT_EMAIL 저장소 시크릿이 설정되어 있는지 확인한다."
  }
}

# 현재 청구는 월 $15.50. 정상이면 닿지 않고, 새 폴링 루프 하나가 붙었을 때는 걸리는 선.
variable "aws_budget_usd" {
  description = "AWS 월 예산(USD). 계정당 예산 2개까지 무료"
  type        = number
  default     = 25
}

# 몇 센트 흔들림까지 매일 메일이 오면 진짜 알림을 무시하게 된다.
variable "anomaly_threshold_usd" {
  description = "이상탐지 알림 최소 영향 금액(USD). 이 미만의 이상은 알리지 않는다"
  type        = number
  default     = 5
}

# --- Google Cloud (여행 모듈, #1050) ---

variable "gcp_project_id" {
  description = "orino GCP 프로젝트 ID"
  type        = string
  default     = "orino-499511"
}

variable "gcp_project_number" {
  description = "orino GCP 프로젝트 번호(예산 필터가 번호를 쓴다)"
  type        = string
  default     = "202935442863"
}

variable "gcp_billing_account" {
  description = "결제 계정 ID. 예산이 붙는 대상(비밀값 아님 — AWS 계정 ID와 같은 식별자)"
  type        = string
  default     = "01EDAC-CEFAA5-004180"
}

variable "gcp_budget_krw" {
  description = "월 예산(원). 정상 사용이면 닿지 않고, 폭주 시 조기에 걸리는 값"
  type        = number
  default     = 30000
}
