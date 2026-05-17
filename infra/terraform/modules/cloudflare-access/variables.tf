variable "account_id" {
  description = "Cloudflare account ID"
  type        = string
}

variable "name" {
  description = "Application display name"
  type        = string
}

variable "domain" {
  description = "Application domain (must match a tunnel CNAME)"
  type        = string
}

variable "type" {
  description = "Access app type: self_hosted (HTTP) or self_hosted (TCP for cloudflared access tcp)"
  type        = string
  default     = "self_hosted"
}

variable "session_duration" {
  description = "Session duration (e.g., 1h, 24h)"
  type        = string
  default     = "1h"
}

variable "allowed_emails" {
  description = "Emails allowed by the policy"
  type        = list(string)
}

variable "require_mfa" {
  description = "Require MFA via Cloudflare Access"
  type        = bool
  default     = true
}

variable "allowed_country_codes" {
  description = "ISO country codes allowed (empty list disables country restriction)"
  type        = list(string)
  default     = []
}

variable "app_launcher_visible" {
  description = "Show the app in the Access launcher"
  type        = bool
  default     = false
}
