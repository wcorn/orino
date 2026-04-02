output "longhorn_backup_bucket" {
  description = "Longhorn backup S3 bucket name"
  value       = module.longhorn_backup.bucket_name
}

output "longhorn_backup_target" {
  description = "Longhorn backup target URL"
  value       = module.longhorn_backup.backup_target
}

output "longhorn_iam_access_key_id" {
  description = "IAM access key ID for Longhorn"
  value       = module.longhorn_backup.iam_access_key_id
}

output "longhorn_iam_secret_access_key" {
  description = "IAM secret access key for Longhorn"
  value       = module.longhorn_backup.iam_secret_access_key
  sensitive   = true
}
