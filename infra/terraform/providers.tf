terraform {
  required_version = ">= 1.14"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
    # 할당량 override(#1151)는 GA provider 에 없는 리소스다 —
    # google_service_usage_consumer_quota_override 는 google-beta 전용이다.
    # (GA 6.50.0 바이너리에 해당 리소스 문자열이 없는 것을 확인했다)
    # 자격증명은 GA 와 같은 ADC 를 쓰므로 CI 인증 설정은 그대로다.
    google-beta = {
      source  = "hashicorp/google-beta"
      version = "~> 6.0"
    }
  }

  backend "s3" {
    bucket = "orino-terraform-state"
    key    = "infra/terraform.tfstate"
    region = "ap-northeast-2"
  }
}

provider "aws" {
  region = var.aws_region
}

# 여행 모듈의 Places/Routes·예산(#1050). 자격증명은 CI가 Workload Identity Federation으로
# 주입한다 — 서비스 계정 키 파일을 만들지 않는다(AWS를 OIDC로만 쓰는 것과 같은 이유).
provider "google" {
  project = var.gcp_project_id
}

# 할당량 override 전용. 별도 자격증명 없이 GA 와 같은 ADC 를 쓴다.
provider "google-beta" {
  project = var.gcp_project_id
}
