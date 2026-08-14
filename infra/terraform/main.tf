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

  # ⏱️ 한시적 — 2026-08-14 켬 / **2026-08-17 끈다**. 끄는 작업은 #1198.
  #
  # #1156 차분 귀속이 막혔다. 계측 15,850/day vs 실청구 49,791/day 로 잔여가
  # 33,941/day(월 $4.58)인데, 유력 후보 2개가 다 반증됐다 — Longhorn 은 블록 존재
  # 확인이 HeadObject(Tier2)라 Tier1 에 안 들어오고, Loki S3.List 는 평균 21.7ms
  # 단일 왕복에 대상 접두사(index/)가 총 94객체라 페이지네이션 증폭이 없다.
  #
  # CE 는 usage type 까지만 쪼개지고 버킷별로는 못 쪼갠다. 버킷 단위 실측이
  # 유일하게 남은 수단이고, 여러 버킷 중 Loki 를 고른 건 **클라이언트 계측값이라는
  # 비교 기준을 가진 유일한 버킷**이기 때문이다. 읽은 값이 16k 면 증폭 없음(잔여는
  # 다른 버킷), 50k 면 Loki 증폭 확정이다.
  #
  # 3일 프로레이트 약 $0.48. 기한을 넘기면 아끼려던 $4.58/월을 측정비가 먹는다 —
  # 하루 $0.16 씩이다. 이 줄과 이 주석은 #1198 에서 통째로 지운다.
  request_metrics_enabled = true
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
