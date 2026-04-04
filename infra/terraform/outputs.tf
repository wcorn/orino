output "longhorn_backup_bucket" {
  description = "Longhorn backup S3 bucket name"
  value       = module.longhorn_backup.bucket_name
}

output "longhorn_backup_target" {
  description = "Longhorn backup target URL"
  value       = "s3://${module.longhorn_backup.bucket_name}@${var.aws_region}/"
}

output "longhorn_iam_access_key_id" {
  description = "IAM access key ID for Longhorn"
  value       = aws_iam_access_key.longhorn.id
}

output "longhorn_iam_secret_access_key" {
  description = "IAM secret access key for Longhorn"
  value       = aws_iam_access_key.longhorn.secret
  sensitive   = true
}

output "loki_logs_bucket" {
  description = "Loki logs S3 bucket name"
  value       = module.loki_logs.bucket_name
}

output "thanos_metrics_bucket" {
  description = "Thanos metrics S3 bucket name"
  value       = module.thanos_metrics.bucket_name
}

output "tempo_traces_bucket" {
  description = "Tempo traces S3 bucket name"
  value       = module.tempo_traces.bucket_name
}

output "observability_iam_access_key_id" {
  description = "IAM access key ID for observability (Loki + Thanos + Tempo)"
  value       = aws_iam_access_key.observability.id
}

output "observability_iam_secret_access_key" {
  description = "IAM secret access key for observability (Loki + Thanos)"
  value       = aws_iam_access_key.observability.secret
  sensitive   = true
}
