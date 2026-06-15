# GitHub Actions CI/CD — 정적 AWS 키 없이 OIDC로 terraform 실행.
# 워크플로우(.github/workflows/terraform.yml)가 이 role을 assume:
#   PR → terraform plan, main 머지 → terraform apply (자동).
# OIDC 인프라 자체도 IaC로 관리(최초 1회 수동 apply로 부트스트랩).

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

resource "aws_iam_role" "terraform_ci" {
  name = "github-actions-terraform"

  # wcorn/orino 저장소의 워크플로우만 assume 가능(다른 repo/계정 차단)
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = { "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com" }
        StringLike   = { "token.actions.githubusercontent.com:sub" = "repo:wcorn/orino:*" }
      }
    }]
  })
}

# terraform이 관리하는 리소스 범위의 권한(Route53 / S3 orino-* / IAM).
# NOTE: iam 은 terraform 이 IAM 사용자·키·이 role 자체를 관리하므로 넓게 부여.
#       trust 가 repo 로 제한돼 노출은 한정적이나, 추후 최소권한 스코핑 여지.
resource "aws_iam_role_policy" "terraform_ci" {
  name = "terraform-ci"
  role = aws_iam_role.terraform_ci.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "Route53"
        Effect   = "Allow"
        Action   = ["route53:*"]
        Resource = "*"
      },
      {
        Sid      = "S3Buckets"
        Effect   = "Allow"
        Action   = ["s3:*"]
        Resource = ["arn:aws:s3:::orino-*", "arn:aws:s3:::orino-*/*"]
      },
      {
        Sid      = "S3ListAll"
        Effect   = "Allow"
        Action   = ["s3:ListAllMyBuckets"]
        Resource = "*"
      },
      {
        Sid      = "IAM"
        Effect   = "Allow"
        Action   = ["iam:*"]
        Resource = "*"
      },
    ]
  })
}

output "terraform_ci_role_arn" {
  description = "GitHub Actions가 assume할 terraform CI role ARN"
  value       = aws_iam_role.terraform_ci.arn
}
