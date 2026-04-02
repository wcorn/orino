module "terraform_state" {
  source = "./modules/s3"

  bucket_name       = "orino-terraform-state"
  enable_versioning = true
}

module "longhorn_backup" {
  source = "./modules/s3"

  bucket_name = "orino-longhorn-backup"
  lifecycle_rules = [
    {
      id = "backup-lifecycle"
      transitions = [
        { days = 30, storage_class = "STANDARD_IA" },
        { days = 90, storage_class = "GLACIER_IR" },
      ]
      expiration_days = 365
    }
  ]
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
