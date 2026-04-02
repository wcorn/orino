output "bucket_name" {
  value = aws_s3_bucket.longhorn_backup.bucket
}

output "bucket_arn" {
  value = aws_s3_bucket.longhorn_backup.arn
}

output "backup_target" {
  description = "Longhorn backup target URL format"
  value       = "s3://${aws_s3_bucket.longhorn_backup.bucket}@${var.aws_region}/"
}

output "iam_access_key_id" {
  value = aws_iam_access_key.longhorn.id
}

output "iam_secret_access_key" {
  value     = aws_iam_access_key.longhorn.secret
  sensitive = true
}
