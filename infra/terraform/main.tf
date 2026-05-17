module "longhorn_backup" {
  source = "./modules/s3"

  bucket_name = "orino-longhorn-backup"
}

# IAM user for Longhorn S3 access
resource "aws_iam_user" "longhorn" {
  name = "longhorn-backup"
}

resource "aws_iam_user_policy" "longhorn" {
  name = "longhorn-backup-s3"
  user = aws_iam_user.longhorn.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject",
          "s3:ListBucket",
        ]
        Resource = [
          module.longhorn_backup.bucket_arn,
          "${module.longhorn_backup.bucket_arn}/*",
        ]
      }
    ]
  })
}

resource "aws_iam_access_key" "longhorn" {
  user = aws_iam_user.longhorn.name
}

# Loki logs S3 bucket
module "loki_logs" {
  source = "./modules/s3"

  bucket_name = "orino-loki-logs"
}

# Thanos metrics S3 bucket
module "thanos_metrics" {
  source = "./modules/s3"

  bucket_name = "orino-thanos-metrics"
}

# Tempo traces S3 bucket
module "tempo_traces" {
  source = "./modules/s3"

  bucket_name = "orino-tempo-traces"
}

# IAM user for observability S3 access (Loki + Thanos + Tempo)
resource "aws_iam_user" "observability" {
  name = "observability-s3"
}

resource "aws_iam_user_policy" "observability" {
  name = "observability-s3"
  user = aws_iam_user.observability.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject",
          "s3:ListBucket",
        ]
        Resource = [
          module.loki_logs.bucket_arn,
          "${module.loki_logs.bucket_arn}/*",
          module.thanos_metrics.bucket_arn,
          "${module.thanos_metrics.bucket_arn}/*",
          module.tempo_traces.bucket_arn,
          "${module.tempo_traces.bucket_arn}/*",
        ]
      }
    ]
  })
}

resource "aws_iam_access_key" "observability" {
  user = aws_iam_user.observability.name
}

# MySQL backup S3 bucket (30-day lifecycle)
module "mysql_backup" {
  source = "./modules/s3"

  bucket_name               = "orino-mysql-backup"
  lifecycle_expiration_days = 30
}

# IAM user for MySQL backup S3 access
resource "aws_iam_user" "mysql_backup" {
  name = "mysql-backup"
}

resource "aws_iam_user_policy" "mysql_backup" {
  name = "mysql-backup-s3"
  user = aws_iam_user.mysql_backup.name

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:DeleteObject",
          "s3:ListBucket",
        ]
        Resource = [
          module.mysql_backup.bucket_arn,
          "${module.mysql_backup.bucket_arn}/*",
        ]
      }
    ]
  })
}

resource "aws_iam_access_key" "mysql_backup" {
  user = aws_iam_user.mysql_backup.name
}

# Random tunnel secret (base64, 33+ bytes)
resource "random_id" "tunnel_secret" {
  byte_length = 35
}

# Cloudflare Tunnel (remotely-managed). Existing tunnel `orino` migrated via:
#   terraform import module.cloudflare_tunnel.cloudflare_zero_trust_tunnel_cloudflared.this <account_id>/<tunnel_id>
module "cloudflare_tunnel" {
  source = "./modules/cloudflare-tunnel"

  account_id    = var.cloudflare_account_id
  zone_id       = var.cloudflare_zone_id
  tunnel_name   = "orino"
  tunnel_secret = random_id.tunnel_secret.b64_std

  ingress = [
    { hostname = "orino.dev", service = "http://istio-gateway.istio-ingress.svc.cluster.local:80" },
    { hostname = "api.orino.dev", service = "http://istio-gateway.istio-ingress.svc.cluster.local:80" },
    { hostname = "telemetry.orino.dev", service = "http://istio-gateway.istio-ingress.svc.cluster.local:80" },
    { hostname = "mysql.orino.dev", service = "tcp://mysql.orino.svc.cluster.local:3306" },
    { service = "http_status:404" },
  ]

  dns_hostnames = [
    "orino.dev",
    "api.orino.dev",
    "telemetry.orino.dev",
    "mysql.orino.dev",
  ]
}

# Cloudflare Access for MySQL (TCP, operator-only)
module "mysql_access" {
  source = "./modules/cloudflare-access"

  account_id            = var.cloudflare_account_id
  name                  = "MySQL"
  domain                = "mysql.orino.dev"
  session_duration      = "1h"
  allowed_emails        = var.operator_emails
  require_mfa           = true
  allowed_country_codes = ["KR"]
}
