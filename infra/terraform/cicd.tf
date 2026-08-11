# GitHub Actions CI/CD — 정적 AWS 키 없이 OIDC로 terraform 실행.
# 워크플로우(.github/workflows/terraform.yml)가 이벤트별로 다른 role을 assume한다:
#   PR(untrusted)       → terraform-plan  role (읽기 전용)  → terraform plan
#   main 머지(trusted)  → terraform-apply role (쓰기 가능)  → terraform apply
# 미신뢰 PR(포크 포함)은 plan(미리보기)만 가능, 어떤 AWS 리소스도 변경 불가.

# Budgets 는 리전 없는 계정 단위 리소스라 ARN(arn:aws:budgets::<account>:budget/*)에
# 계정 ID 가 필요하다. sts:GetCallerIdentity 는 IAM 권한 없이 누구나 호출할 수 있어
# plan/apply 양쪽 role 에 추가 권한이 들지 않는다.
data "aws_caller_identity" "current" {}

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
      # --- 비용 관리 체계(#1148) — 예산 리소스를 만들기 전에 만들 권한을 먼저 연다 ---
      # 태깅 액션까지 넣는 이유: aws_budgets_budget 은 tags 를 지원하므로 provider 가
      # Read 마다 ListTagsForResource 를 부른다. 빼면 apply 가 아니라 refresh 에서 깨진다.
      {
        Sid    = "Budgets"
        Effect = "Allow"
        Action = [
          "budgets:ViewBudget", "budgets:ModifyBudget",
          "budgets:TagResource", "budgets:UntagResource", "budgets:ListTagsForResource",
        ]
        Resource = "arn:aws:budgets::${data.aws_caller_identity.current.account_id}:budget/*"
      },
      # Cost Anomaly Detection — 모니터·구독 라이프사이클과 태그만 부여한다.
      # ce:GetCostAndUsage 같은 유료 조회 API(건당 $0.01)는 주지 않는다: CI 가
      # 실수로 유료 조회를 돌 수 있게 만들 이유가 없다.
      # 와일드카드(ce:*AnomalyMonitor)를 쓰지 않는 건 기존 IAM 블록의 원칙이기도 하지만,
      # 조회 액션이 복수형(GetAnomalyMonitors)이라 그 패턴에 아예 걸리지도 않는다.
      {
        Sid    = "CostAnomalyDetection"
        Effect = "Allow"
        Action = [
          "ce:CreateAnomalyMonitor", "ce:GetAnomalyMonitors",
          "ce:UpdateAnomalyMonitor", "ce:DeleteAnomalyMonitor",
          "ce:CreateAnomalySubscription", "ce:GetAnomalySubscriptions",
          "ce:UpdateAnomalySubscription", "ce:DeleteAnomalySubscription",
          "ce:TagResource", "ce:UntagResource", "ce:ListTagsForResource",
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
      # --- 비용 관리 체계(#1148) refresh 용 읽기 ---
      {
        Sid      = "BudgetsRead"
        Effect   = "Allow"
        Action   = ["budgets:ViewBudget", "budgets:ListTagsForResource"]
        Resource = "arn:aws:budgets::${data.aws_caller_identity.current.account_id}:budget/*"
      },
      # 다른 읽기 블록과 달리 ce 는 접두사 와일드카드(ce:Get*)를 쓰지 않는다.
      # ce:Get* 는 GetCostAndUsage(호출당 $0.01)를 포함하고, 이 role 은 포크 PR 도
      # assume 할 수 있다 — 미신뢰 PR 이 유료 API 를 부를 수 있는 경로를 만들지 않는다.
      # refresh 가 실제로 부르는 것만 넣는다.
      {
        Sid    = "CostAnomalyDetectionRead"
        Effect = "Allow"
        Action = [
          "ce:GetAnomalyMonitors", "ce:GetAnomalySubscriptions", "ce:ListTagsForResource",
        ]
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
