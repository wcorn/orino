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

# 버킷 단위 요청 수를 CloudWatch 로 내보낸다. Cost Explorer 는 usage type 까지만
# 쪼개지고 버킷별로는 못 쪼개므로, 차분 귀속이 막혔을 때 유일한 실측 수단이다.
#
# filter 를 비워 버킷 전체를 하나로 집계한다(접두사별로 쪼개면 필터당 과금이 는다).
# name 은 AWS 관례상 "EntireBucket" 을 쓴다.
resource "aws_s3_bucket_metric" "requests" {
  count  = var.request_metrics_enabled ? 1 : 0
  bucket = aws_s3_bucket.this.id
  name   = "EntireBucket"
}

# 조건을 걸지 않는다 — 아래 두 규칙 중 하나는 항상 켜야 하기 때문이다.
#
# 만료 규칙은 버킷마다 다르지만(백업은 영구 보관, 로그는 30일), **묘비 청소는 모든
# 버킷이 켜야 한다.** 안 켜면 지표가 오염되고, 오염된 지표는 오진을 만든다 — #1211 이
# `NumberOfObjects` 289,271 을 보고 "블록이 회수되지 않는다"고 2 개월간 오진했는데,
# 실제 객체는 35,250 이고 나머지 257,942 가 삭제 표식이었다. 자세한 경위는 #1232.
resource "aws_s3_bucket_lifecycle_configuration" "this" {
  bucket = aws_s3_bucket.this.id

  # 만료 규칙을 먼저 둔다. rule 은 목록으로 비교되므로 순서가 바뀌면 이미 이 규칙을
  # 가진 버킷(mysql-backup)의 diff 가 "id 가 바뀐다"처럼 보인다. 결과는 같지만
  # 리뷰에서 오해를 사고, 오해를 사는 diff 는 다음번에 진짜 변경을 가린다.
  #
  # days 와 expired_object_delete_marker 는 같은 rule 에 못 들어간다(AWS 제약).
  # 그래서 만료는 별도 rule 로 나누고, 필요 없는 버킷에서는 통째로 뺀다.
  dynamic "rule" {
    for_each = var.lifecycle_expiration_days > 0 ? [var.lifecycle_expiration_days] : []

    content {
      id     = "expire-after-${rule.value}-days"
      status = "Enabled"

      filter {}

      expiration {
        days = rule.value
      }
    }
  }

  # versioning 을 한 번이라도 켠 버킷은 DELETE 가 객체를 지우는 대신 삭제 표식을
  # 남긴다. Suspended 로 되돌려도 이미 쌓인 표식은 스스로 사라지지 않는다.
  #
  # expired_object_delete_marker 는 **밑에 아무 버전도 남지 않은** 표식만 지운다.
  # 살아 있는 데이터를 건드릴 수 없는 규칙이라 versioning 이 꺼진 버킷에서는 그냥
  # 무동작이고, 켜진 적 있는 버킷에서만 청소가 일어난다. 그래서 전 버킷 공통이다.
  #
  # 표식 삭제는 lifecycle 이 수행하면 요금이 붙지 않는다. 이 규칙은 비용을 줄이려는
  # 게 아니라 **NumberOfObjects 가 다시 실제 객체 수를 가리키게 하려는 것**이다.
  rule {
    id     = "cleanup-expired-delete-markers"
    status = "Enabled"

    filter {}

    expiration {
      expired_object_delete_marker = true
    }
  }
}
