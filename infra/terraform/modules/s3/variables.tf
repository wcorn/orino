variable "bucket_name" {
  description = "S3 bucket name"
  type        = string
}

variable "enable_versioning" {
  description = "Enable S3 bucket versioning"
  type        = bool
  default     = false
}

variable "lifecycle_rules" {
  description = "Lifecycle rules for the bucket"
  type = list(object({
    id = string
    transitions = list(object({
      days          = number
      storage_class = string
    }))
    expiration_days = optional(number)
  }))
  default = []
}
