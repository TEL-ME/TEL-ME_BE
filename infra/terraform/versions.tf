terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # state 버킷은 콘솔에서 미리 만듬(버전 관리, 암호화, 퍼블릭 차단). 잠금은 S3 lock 파일로
  backend "s3" {
    bucket       = "telme-tfstate-989624287827"
    key          = "prod/terraform.tfstate"
    region       = "ap-northeast-2"
    profile      = "telme"
    encrypt      = true
    use_lockfile = true
  }
}

provider "aws" {
  region  = var.region
  profile = var.aws_profile

  default_tags {
    tags = {
      Project   = "telme"
      Env       = "prod"
      ManagedBy = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}
