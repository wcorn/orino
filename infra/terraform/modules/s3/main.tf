resource "aws_s3_bucket" "this" {
  bucket = var.bucket_name
}

resource "aws_s3_bucket_server_side_encryption_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  # blocked_encryption_types · bucket_key_enabled 를 명시하는 이유는 설정을 바꾸려는
  # 게 아니라 영구 diff 를 멈추기 위해서다. AWS 가 버킷에 blocked_encryption_types =
  # ["SSE-C"] 를 스스로 채우는데 코드에 그 속성이 없으면, 매 refresh 마다 실제 상태와
  # 코드가 어긋나 apply 가 이 버킷을 항상 "변경됨"으로 잡는다(실제로 두 번 연속
  # 같은 5건이 왕복했다). bucket_key_enabled 도 실제 false / 코드 미지정으로 같은 상황.
  # rule 은 집합 원소로 비교되므로 둘 중 하나만 맞춰서는 diff 가 남는다 — 둘 다 적는다.
  #
  # 값 자체는 현재 AWS 실제 상태 그대로다. SSE-C(고객 제공 키) 차단은 유지하는 편이
  # 안전하고, 안 적으면 apply 마다 벗겨진다.
  #
  # 무관한 변경 5줄이 매번 뜨면 진짜 변경을 가린다. 항상 켜져 있는 경고등은 꺼진
  # 경고등과 같다 — ELOG-027 이 5개월간 안 보였던 것과 같은 종류의 마모다. #1171
  rule {
    bucket_key_enabled       = false
    blocked_encryption_types = ["SSE-C"]

    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "this" {
  bucket = aws_s3_bucket.this.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_lifecycle_configuration" "this" {
  count  = var.lifecycle_expiration_days > 0 ? 1 : 0
  bucket = aws_s3_bucket.this.id

  rule {
    id     = "expire-after-${var.lifecycle_expiration_days}-days"
    status = "Enabled"

    filter {}

    expiration {
      days = var.lifecycle_expiration_days
    }
  }
}
