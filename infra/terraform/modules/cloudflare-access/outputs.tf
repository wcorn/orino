output "application_id" {
  description = "Access Application ID"
  value       = cloudflare_zero_trust_access_application.this.id
}

output "application_aud" {
  description = "Access Application AUD (use for JWT validation if needed)"
  value       = cloudflare_zero_trust_access_application.this.aud
}
