variable "bucket_name" {
  description = "S3 bucket name"
  type        = string
}

variable "lifecycle_expiration_days" {
  description = "Object expiration in days. 0 = disabled (no lifecycle rule)."
  type        = number
  default     = 0
}

# 켜면 요금이 붙는다 — CloudWatch 유료 메트릭 16종 × $0.30/월 = 버킷당 $4.8/월.
# 항상 켜 두면 안 되고, 진단이 끝나면 반드시 끈다. 기본값이 false 인 이유다.
variable "request_metrics_enabled" {
  description = "S3 request metrics(CloudWatch). 버킷당 약 $4.8/월 과금 — 진단 목적으로 한시적으로만 켠다"
  type        = bool
  default     = false
}
