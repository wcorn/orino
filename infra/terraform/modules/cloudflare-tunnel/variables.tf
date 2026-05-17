variable "account_id" {
  description = "Cloudflare account ID"
  type        = string
}

variable "zone_id" {
  description = "Cloudflare zone ID"
  type        = string
}

variable "tunnel_name" {
  description = "Tunnel name (existing tunnel must be imported)"
  type        = string
}

variable "tunnel_secret" {
  description = "Base64-encoded tunnel secret (33+ bytes)"
  type        = string
  sensitive   = true
}

variable "ingress" {
  description = "Ingress rules. Last entry must be the catch-all (service = http_status:404)."
  type = list(object({
    hostname = optional(string)
    path     = optional(string)
    service  = string
  }))
}

variable "dns_hostnames" {
  description = "Hostnames to create proxied CNAME records for, pointing to the tunnel. Must be subdomains of the zone."
  type        = list(string)
}
