variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
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
