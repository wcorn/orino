variable "aws_region" {
  description = "AWS region"
  type        = string
  default     = "ap-northeast-2"
}

variable "cloudflare_api_token" {
  description = "Cloudflare API token (Tunnel + Access + DNS Edit)"
  type        = string
  sensitive   = true
}

variable "cloudflare_account_id" {
  description = "Cloudflare account ID"
  type        = string
}

variable "cloudflare_zone_id" {
  description = "Cloudflare zone ID for orino.dev"
  type        = string
}

variable "operator_emails" {
  description = "Allowed operator emails for Access policies"
  type        = list(string)
}
