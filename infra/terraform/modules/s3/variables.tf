variable "bucket_name" {
  description = "S3 bucket name"
  type        = string
}

variable "lifecycle_expiration_days" {
  description = "Object expiration in days. 0 = disabled (no lifecycle rule)."
  type        = number
  default     = 0
}
