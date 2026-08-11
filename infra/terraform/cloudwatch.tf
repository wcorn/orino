# Grafana CloudWatch 데이터소스용 읽기 전용 자격증명 (#1153 · Epic #1148).
#
# 버킷 객체 수·용량(BucketSizeBytes · NumberOfObjects)은 Prometheus 로 알 수 없다 —
# Loki retention 이 5개월간 집행되지 않은 걸 아무도 못 본 게 이 지표가 없어서였다
# (ELOG-027). 객체 수 추이를 봐야 "선언된 정책이 실제로 도는지"가 보인다.
#
# ⚠️ 기존 observability-s3 키를 재사용하지 않는다. 그 키는 S3 읽기·쓰기 권한이 있고
# Loki/Thanos/Tempo 가 쓰는 운영 키다. 대시보드가 읽기만 하면 되는데 쓰기 권한을 가진
# 키를 Grafana 에 넣을 이유가 없다.
#
# ⚠️ GetMetricData 는 metric 당 과금($0.01/1,000)이다. 버킷 6개 × 2메트릭 = 12 를
# 1시간 주기로 조회하면 월 8,640건 = $0.09, 1분 주기면 월 $5 로 남은 절감 여지
# 전체($2.8/월)를 넘어선다. 주기 제한은 Grafana 쪽(데이터소스·알림 평가 주기)에서
# 건다 — IAM 으로는 호출 빈도를 제한할 수 없다. 관측이 과금을 만드는 함정을
# 이 프로젝트는 두 번 겪었다.
resource "aws_iam_user" "cloudwatch_readonly" {
  name = "grafana-cloudwatch-readonly"
}

resource "aws_iam_user_policy" "cloudwatch_readonly" {
  name = "grafana-cloudwatch-readonly"
  user = aws_iam_user.cloudwatch_readonly.name

  # cloudwatch:* 를 쓰지 않는다. 대시보드가 실제로 부르는 3개만 —
  # 알람 생성·삭제(PutMetricAlarm 등)나 지표 쓰기(PutMetricData)는 필요 없다.
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "CloudWatchRead"
        Effect = "Allow"
        Action = [
          "cloudwatch:GetMetricData",
          "cloudwatch:GetMetricStatistics",
          "cloudwatch:ListMetrics",
        ]
        Resource = "*"
      },
    ]
  })
}

resource "aws_iam_access_key" "cloudwatch_readonly" {
  user = aws_iam_user.cloudwatch_readonly.name
}

output "cloudwatch_readonly_access_key_id" {
  description = "Grafana CloudWatch 데이터소스용 access key ID"
  value       = aws_iam_access_key.cloudwatch_readonly.id
}

output "cloudwatch_readonly_secret_access_key" {
  description = "Grafana CloudWatch 데이터소스용 secret access key (SealedSecret 로 봉인해 사용)"
  value       = aws_iam_access_key.cloudwatch_readonly.secret
  sensitive   = true
}
