# GitHub Actions CI/CD — 정적 AWS 키 없이 OIDC로 terraform 실행.
# 워크플로우(.github/workflows/terraform.yml)가 이벤트별로 다른 role을 assume한다:
#   PR(untrusted)       → terraform-plan  role (읽기 전용)  → terraform plan
#   main 머지(trusted)  → terraform-apply role (쓰기 가능)  → terraform apply
# 미신뢰 PR(포크 포함)은 plan(미리보기)만 가능, 어떤 AWS 리소스도 변경 불가.

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

# ---------------------------------------------------------------------------
# apply role — main 브랜치 push 만 assume 가능(머지 후 trusted 경로). 쓰기 권한.
# ---------------------------------------------------------------------------
resource "aws_iam_role" "terraform_apply" {
  name = "github-actions-terraform"

  # main 브랜치 워크플로우만 assume(PR·다른 브랜치·다른 repo 차단).
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          "token.actions.githubusercontent.com:sub" = "repo:wcorn/orino:ref:refs/heads/main"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "terraform_apply" {
  name = "terraform-apply"
  role = aws_iam_role.terraform_apply.id

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
      # iam:* 대신 terraform 이 실제 관리하는 리소스(유저·인라인정책·액세스키·role·OIDC)
      # 라이프사이클 액션만 부여. 로그인프로필·MFA·SAML·관리형정책 연결·권한경계 등 미사용 위험 액션 제외.
      {
        Sid    = "IamUsers"
        Effect = "Allow"
        Action = [
          "iam:CreateUser", "iam:GetUser", "iam:DeleteUser", "iam:UpdateUser",
          "iam:TagUser", "iam:UntagUser", "iam:ListUserTags", "iam:ListUsers",
          "iam:PutUserPolicy", "iam:GetUserPolicy", "iam:DeleteUserPolicy", "iam:ListUserPolicies",
          "iam:CreateAccessKey", "iam:DeleteAccessKey", "iam:UpdateAccessKey",
          "iam:ListAccessKeys", "iam:GetAccessKeyLastUsed",
        ]
        Resource = "*"
      },
      {
        Sid    = "IamRoles"
        Effect = "Allow"
        Action = [
          "iam:CreateRole", "iam:GetRole", "iam:DeleteRole", "iam:UpdateRole",
          "iam:UpdateAssumeRolePolicy", "iam:TagRole", "iam:UntagRole", "iam:ListRoleTags",
          "iam:PutRolePolicy", "iam:GetRolePolicy", "iam:DeleteRolePolicy", "iam:ListRolePolicies",
          "iam:ListAttachedRolePolicies", "iam:ListInstanceProfilesForRole", "iam:ListRoles",
        ]
        Resource = "*"
      },
      {
        Sid    = "IamOidc"
        Effect = "Allow"
        Action = [
          "iam:CreateOpenIDConnectProvider", "iam:GetOpenIDConnectProvider",
          "iam:DeleteOpenIDConnectProvider", "iam:UpdateOpenIDConnectProviderThumbprint",
          "iam:TagOpenIDConnectProvider", "iam:UntagOpenIDConnectProvider",
          "iam:ListOpenIDConnectProviderTags", "iam:ListOpenIDConnectProviders",
        ]
        Resource = "*"
      },
    ]
  })
}

# ---------------------------------------------------------------------------
# plan role — PR 만 assume 가능(untrusted). 읽기 전용 + state(orino-terraform-state) 읽기.
# 미신뢰 PR(포크 포함)은 plan(미리보기)만 가능, 어떤 리소스도 변경 불가.
# ---------------------------------------------------------------------------
resource "aws_iam_role" "terraform_plan" {
  name = "github-actions-terraform-plan"

  # pull_request 이벤트 토큰만 assume. push/main 은 apply role 만 사용.
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          "token.actions.githubusercontent.com:sub" = "repo:wcorn/orino:pull_request"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "terraform_plan" {
  name = "terraform-plan"
  role = aws_iam_role.terraform_plan.id

  # plan 은 state 읽기 + 리소스 refresh(읽기)만 필요. 백엔드에 lock 테이블이 없어 쓰기 없음.
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "Route53Read"
        Effect   = "Allow"
        Action   = ["route53:Get*", "route53:List*"]
        Resource = "*"
      },
      {
        # orino-* (state 버킷 orino-terraform-state 포함) 읽기.
        Sid      = "S3Read"
        Effect   = "Allow"
        Action   = ["s3:Get*", "s3:List*"]
        Resource = ["arn:aws:s3:::orino-*", "arn:aws:s3:::orino-*/*"]
      },
      {
        Sid      = "S3ListAll"
        Effect   = "Allow"
        Action   = ["s3:ListAllMyBuckets"]
        Resource = "*"
      },
      {
        Sid      = "IamRead"
        Effect   = "Allow"
        Action   = ["iam:Get*", "iam:List*", "iam:GenerateServiceLastAccessedDetails"]
        Resource = "*"
      },
    ]
  })
}

output "terraform_apply_role_arn" {
  description = "main 머지 시 apply에 사용하는 role ARN"
  value       = aws_iam_role.terraform_apply.arn
}

output "terraform_plan_role_arn" {
  description = "PR plan(읽기 전용)에 사용하는 role ARN"
  value       = aws_iam_role.terraform_plan.arn
}
