variable "bucket_name" {
  description = "S3 bucket name for Longhorn backup"
  type        = string
}

variable "aws_region" {
  description = "AWS region"
  type        = string
}

variable "lifecycle_ia_days" {
  description = "Days before transitioning to Standard-IA"
  type        = number
  default     = 30
}

variable "lifecycle_glacier_days" {
  description = "Days before transitioning to Glacier Instant Retrieval"
  type        = number
  default     = 90
}

variable "lifecycle_expiration_days" {
  description = "Days before expiring old backups"
  type        = number
  default     = 365
}
