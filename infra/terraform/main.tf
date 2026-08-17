module "longhorn_backup" {
  source = "./modules/s3"

  bucket_name = "orino-longhorn-backup"

  # ⏱️ 한시적 — 2026-08-18 켬 / **2026-08-21 끈다**. 끄는 작업은 #1211.
  #
  # Tier1 잔여 약 44k/day(월 $6.6)가 소거 끝에 이 버킷 하나로 몰렸다. #1198 에서
  # Loki 버킷을 실측해 실청구 14,486 vs 계측 14,494(오차 0.06%)로 증폭을 반증했고,
  # 나머지는 객체 수로 상한이 막힌다 — thanos 226 · tempo 691 · state 42 · mysql 30.
  # 여기만 281,442 객체이고 **클라이언트 계측이 없는 유일한 버킷**이다.
  #
  # 다만 알려진 Tier1 경로는 전부 닫혀 있다. 폴링은 ELOG-008 에서 5m→0 으로 껐고
  # (backuptargets.longhorn.io/default pollInterval=0), 업로드는 하루 2,050 객체라
  # 자릿수가 다르며, 블록 존재 확인은 HeadObject(Tier2)로 #1156 에서 반증됐다.
  # 그래서 위치는 소거로 좁혀졌지만 메커니즘은 모른다 — 실측만이 남은 수단이다.
  #
  # 3일 프로레이트 약 $0.48. 기한을 넘기면 아끼려던 $6.6/월을 측정비가 먹는다 —
  # 하루 $0.16 씩이다. 이 줄과 이 주석은 #1211 에서 통째로 지운다.
  request_metrics_enabled = true
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
