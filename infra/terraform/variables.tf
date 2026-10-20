variable "aws_profile" {
  description = "배포 계정(989624287827)의 AWS CLI 프로필"
  type        = string
  default     = "telme"
}

variable "region" {
  type    = string
  default = "ap-northeast-2"
}

variable "name" {
  description = "자원 이름 접두사"
  type        = string
  default     = "telme"
}

# ---------- 네트워크 ----------

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "azs" {
  description = "서브넷을 둘 가용 영역. g6.xlarge가 있는 2a·2c"
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

# ---------- 앱 ----------

variable "app_instance_type" {
  type    = string
  default = "t3.large"
}

variable "app_instance_count" {
  description = "ASG 최소·희망 대수. 첫 apply는 이미지가 없으니 0, ECR에 이미지를 올린 뒤 1"
  type        = number
  default     = 0
}

variable "app_max_size" {
  type    = number
  default = 2
}

variable "app_image_tag" {
  description = "SSM 이미지 태그 파라미터를 처음 만들 때만 쓰는 값. 만든 뒤로는 바꿔도 반영되지 않는다(ignore_changes). 태그는 CLI나 CD가 /telme/prod/deploy/image_tag에 쓴다"
  type        = string
  default     = "initial"
}

variable "tei_image" {
  description = "앱 EC2에서 CPU로 돌리는 bge-m3 임베딩 서버"
  type        = string
  default     = "ghcr.io/huggingface/text-embeddings-inference:cpu-1.8"
}

# ---------- DB ----------

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "db_engine_version" {
  type    = string
  default = "16"
}

variable "db_name" {
  type    = string
  default = "telme"
}

variable "db_username" {
  type    = string
  default = "telme"
}

# ---------- LLM ----------

variable "bedrock_region" {
  description = "gpt-oss-120b를 부르는 Bedrock 리전(도쿄로 확정, 2026-10-20)"
  type        = string
  default     = "ap-northeast-1"
}

variable "bedrock_model_id" {
  type    = string
  default = "openai.gpt-oss-120b-1:0"
}

variable "gpu_instance_type" {
  type    = string
  default = "g6.xlarge"
}

variable "gpu_volume_size" {
  type    = number
  default = 100
}

# ---------- 도메인 ----------

variable "domain_names" {
  description = "CloudFront에 붙일 도메인. 첫 번째가 서비스 주소. 비우면 CloudFront 기본 주소(*.cloudfront.net)를 쓴다"
  type        = list(string)
  default     = []
}

variable "acm_certificate_arn" {
  description = "domain_names를 담은 ACM 인증서. CloudFront용이라 us-east-1에서 콘솔로 받는다(Terraform 밖)"
  type        = string
  default     = ""

  validation {
    condition     = (var.acm_certificate_arn == "") == (length(var.domain_names) == 0)
    error_message = "domain_names와 acm_certificate_arn은 둘 다 넣거나 둘 다 비워야 한다."
  }

  validation {
    condition     = var.acm_certificate_arn == "" || startswith(var.acm_certificate_arn, "arn:aws:acm:us-east-1:")
    error_message = "CloudFront 인증서는 us-east-1에서 받아야 한다."
  }
}

# ---------- CI/CD ----------

variable "github_oidc_subjects" {
  description = "배포 역할을 맡을 수 있는 GitHub Actions 주체(레포·브랜치)"
  type        = list(string)
  # 두 레포는 OIDC sub에 변경 불가 식별자(이름@숫자 ID)를 쓴다(use_immutable_subject).
  # 확인: gh api repos/TEL-ME/<레포>/actions/oidc/customization/sub 의 sub_claim_prefix
  default = [
    "repo:TEL-ME@326893416/TEL-ME_BE@1362451590:ref:refs/heads/develop",
    "repo:TEL-ME@326893416/TEL-ME_FE@1362451895:ref:refs/heads/develop",
  ]
}

# ---------- 알림 ----------

variable "alarm_email" {
  description = "CloudWatch 알람을 받을 이메일. apply 뒤 메일함에서 구독 확인을 눌러야 한다"
  type        = string
}

variable "log_retention_days" {
  type    = number
  default = 7
}
