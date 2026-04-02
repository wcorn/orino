resource "aws_s3_bucket" "longhorn_backup" {
  bucket = var.bucket_name
}

resource "aws_s3_bucket_lifecycle_configuration" "longhorn_backup" {
  bucket = aws_s3_bucket.longhorn_backup.id

  rule {
    id     = "backup-lifecycle"
    status = "Enabled"

    transition {
      days          = var.lifecycle_ia_days
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = var.lifecycle_glacier_days
      storage_class = "GLACIER_IR"
    }

    expiration {
      days = var.lifecycle_expiration_days
    }
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "longhorn_backup" {
  bucket = aws_s3_bucket.longhorn_backup.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "longhorn_backup" {
  bucket = aws_s3_bucket.longhorn_backup.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "longhorn_backup" {
  bucket = aws_s3_bucket.longhorn_backup.id

  versioning_configuration {
    status = "Suspended"
  }
}

# IAM user for Longhorn
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
          aws_s3_bucket.longhorn_backup.arn,
          "${aws_s3_bucket.longhorn_backup.arn}/*",
        ]
      }
    ]
  })
}

resource "aws_iam_access_key" "longhorn" {
  user = aws_iam_user.longhorn.name
}
