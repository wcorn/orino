# Route 53 — orino.dev 권한 DNS (Cloudflare 제거 → 가비아에서 NS 위임). 이슈 #456
#
# Phase 0: hosted zone 만 생성해 NS 4개를 확보한다.
#   → 가비아 콘솔에서 이 NS 로 위임(전파 수시간~1일)하면 Route53 이 권한 DNS 가 된다.
# A 레코드(orino/api/img)는 DDNS(ddns-updater)가 동적 WAN IP 로 관리하므로
# 별도 단계에서 ignore_changes 와 함께 추가한다.

resource "aws_route53_zone" "orino" {
  name    = "orino.dev"
  comment = "orino.dev authoritative DNS — Cloudflare 제거, 집 회선 직결 (#456)"
}

output "route53_orino_zone_id" {
  description = "orino.dev Route53 hosted zone ID (cert-manager / DDNS / A레코드에서 참조)"
  value       = aws_route53_zone.orino.zone_id
}

output "route53_orino_name_servers" {
  description = "가비아 콘솔에 입력할 NS 4개 (orino.dev 위임용)"
  value       = aws_route53_zone.orino.name_servers
}
