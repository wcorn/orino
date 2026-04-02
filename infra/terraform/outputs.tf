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
