# AWS 비용 알림 (#1150 · Epic #1148).
#
# 지금까지 관측은 지출과 정확히 반대로 걸려 있었다 — GCP는 예산 알림이 있는데 실지출이
# ~$0이고, 실제로 돈이 나가는 AWS($15.50/월)에는 알림이 없었다. AWS에서 무슨 일이 나면
# 사람이 Cost Explorer를 CLI로 조회할 때까지(건당 $0.01) 아무도 모르는 상태였다.
#
# ── 온프레미스를 경유하지 않는다 ──────────────────────────────────────────────
# Grafana는 note1/note2 위에 있다. 클러스터가 죽어서 백업 잡이 미쳐 도는 상황이 바로
# 알림이 필요한 순간인데, 그때 Grafana 경유 알림은 같이 죽는다. 여기 알림은
# AWS → 이메일 직결이어야 한다. 빠른 탐지(Prometheus 레이어)는 #1153이 담당한다.
#
# ── SNS를 두지 않는다 ────────────────────────────────────────────────────────
# Budgets도 Anomaly Subscription도 이메일 구독자를 직접 받는다. SNS를 끼우면 토픽 정책·
# 구독 확인·IAM이 늘어나는데 얻는 게 없다. IMMEDIATE 알림은 SNS가 필수지만 AWS 비용
# 데이터 자체가 하루 가까이 지연되므로 실제 탐지시간은 달라지지 않는다 — 목표(G1-b)는
# 2일이고 DAILY로 충분하다. 중간 구성요소가 하나 줄면 고장날 곳도 하나 준다.

# 계정당 2개까지 무료. $25는 현재 $15.50에 여유를 둔 값 — 정상이면 닿지 않고,
# 새 폴링 루프 하나가 붙었을 때는 걸린다(ELOG-027의 루프 하나가 연 환산 $36이었다).
resource "aws_budgets_budget" "monthly" {
  name         = "orino-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.aws_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # 실제 지출 80% — 아직 여유가 있을 때 먼저 안다.
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 80
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = var.budget_alert_emails
  }

  # 실제 지출 100% — 상한에 닿았다.
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = var.budget_alert_emails
  }

  # 예측 100% — 월말에 상한을 넘길 추세면 닿기 전에 알린다. 실제로 상한을 넘긴 뒤
  # 알리는 것보다 이쪽이 먼저 울리는 게 정상이다(GCP 예산의 FORECASTED_SPEND와 같은 역할).
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = var.budget_alert_emails
  }
}

# 비용 이상탐지 — 무료. 예산은 "총액이 선을 넘었나"만 보지만 이쪽은 서비스별 패턴에서
# 벗어난 지출을 잡는다. 총액이 $25 안이어도 특정 서비스가 갑자기 튀면 걸린다.
#
# ⚠️ 베이스라인 학습에 열흘쯤 걸린다. 켠 다음 날 조용한 것은 고장이 아니다.
resource "aws_ce_anomaly_monitor" "service" {
  name              = "orino-service-monitor"
  monitor_type      = "DIMENSIONAL"
  monitor_dimension = "SERVICE"
}

resource "aws_ce_anomaly_subscription" "service" {
  name      = "orino-anomaly-daily"
  frequency = "DAILY"

  monitor_arn_list = [aws_ce_anomaly_monitor.service.arn]

  dynamic "subscriber" {
    for_each = var.budget_alert_emails
    content {
      type    = "EMAIL"
      address = subscriber.value
    }
  }

  # 임계 미만의 이상은 알리지 않는다. 취미 클러스터 규모에서 몇 센트 흔들림까지
  # 매일 메일이 오면 진짜 알림을 무시하게 된다.
  threshold_expression {
    dimension {
      key           = "ANOMALY_TOTAL_IMPACT_ABSOLUTE"
      match_options = ["GREATER_THAN_OR_EQUAL"]
      values        = [tostring(var.anomaly_threshold_usd)]
    }
  }
}
