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
      # Grafana 의 CloudWatch health check 는 metrics 와 logs 를 둘 다 검사한다.
      # 이 액션이 없으면 패널이 멀쩡히 동작해도 데이터소스가 **영구히 빨간 ERROR** 로
      # 보인다. 항상 켜져 있는 경고등은 꺼진 경고등과 같다 — 이 프로젝트가 알림에
      # 대해 세운 원칙이 상태 표시에도 그대로 적용된다. 게다가 그 빨간 배지를 보고
      # 누군가 logs:* 를 통째로 붙이는 게 진짜 위험이다.
      #
      # 부르는 API 가 DescribeLogGroups 하나뿐인 것은 CloudTrail 로 확인했다(#1179).
      #
      # ⚠️ Logs Insights 계열(StartQuery·GetQueryResults·FilterLogEvents)은 일부러
      # 주지 않는다. 그쪽이 **스캔 GB 당 과금**되는 경로다 — Grafana 에서 실수로
      # 로그 쿼리를 돌려 요금이 나가는 길을 열지 않는다. 관측이 과금을 만드는
      # 함정을 이 프로젝트는 두 번 겪었다.
      # 결과적으로 health check 는 통과하고(배지 초록), 유료 경로는 닫혀 있다.
      {
        Sid      = "CloudWatchLogsHealthCheckOnly"
        Effect   = "Allow"
        Action   = ["logs:DescribeLogGroups"]
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
