# GitHub Actions CI/CD — 정적 AWS 키 없이 OIDC로 terraform 실행.
# 신뢰 경계에 따라 role 을 분리한다(2단계 마이그레이션):
#   apply role(쓰기) — 기존 단일 role. 현재는 repo 전체 워크플로우가 assume.
#   plan  role(읽기) — 신규. PR(sub=pull_request)만 assume. 미신뢰 PR 은 미리보기만.
# 이 PR(Phase 1)은 plan role 을 만들고 apply IAM 을 스코핑한다. 워크플로우 분기와
# apply trust 축소(main push 전용)는 plan role 이 생긴 뒤 Phase 2(후속 PR)에서 한다
# — 그래야 PR plan 이 끊기지 않고(부트스트랩) 두 PR 모두 CI 가 통과한다.

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1"]
}

# 기존 단일 role(terraform_ci)을 apply role로 명칭 정리. AWS 이름은 유지 → state만 이동(재생성 없음).
moved {
  from = aws_iam_role.terraform_ci
  to   = aws_iam_role.terraform_apply
}

moved {
  from = aws_iam_role_policy.terraform_ci
  to   = aws_iam_role_policy.terraform_apply
}

# ---------------------------------------------------------------------------
# apply role — 쓰기 권한. (Phase 2 에서 trust 를 main push 전용으로 좁힐 예정)
# ---------------------------------------------------------------------------
resource "aws_iam_role" "terraform_apply" {
  name = "github-actions-terraform"

  # 현재는 wcorn/orino 의 모든 워크플로우가 assume 가능(기존 동작 유지).
  # plan role 이 생성된 뒤(Phase 2) main push 전용으로 좁힌다 — 그래야 부트스트랩이 끊기지 않음.
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
