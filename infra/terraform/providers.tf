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
