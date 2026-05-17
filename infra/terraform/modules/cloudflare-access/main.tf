resource "cloudflare_zero_trust_access_application" "this" {
  account_id           = var.account_id
  name                 = var.name
  domain               = var.domain
  type                 = var.type
  session_duration     = var.session_duration
  app_launcher_visible = var.app_launcher_visible
}

resource "cloudflare_zero_trust_access_policy" "allow" {
  account_id     = var.account_id
  application_id = cloudflare_zero_trust_access_application.this.id
  name           = "${var.name} - allow"
  precedence     = 1
  decision       = "allow"

  include {
    email = var.allowed_emails
  }

  dynamic "require" {
    for_each = var.require_mfa ? [1] : []
    content {
      auth_method = "mfa"
    }
  }

  dynamic "require" {
    for_each = length(var.allowed_country_codes) > 0 ? [1] : []
    content {
      geo = var.allowed_country_codes
    }
  }
}
