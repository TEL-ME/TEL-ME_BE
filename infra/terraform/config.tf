# 앱 EC2의 user data가 /telme/prod/env/ 아래 값을 모두 읽어 컨테이너 환경 변수로 넣는다.
# 값은 콘솔이나 CLI로 넣는다(state에 평문이 남지 않게)

locals {
  env_prefix = "/${var.name}/prod/env"

  app_env = {
    SPRING_PROFILES_ACTIVE = "prod"

    POSTGRES_HOST = aws_db_instance.main.address
    POSTGRES_PORT = tostring(aws_db_instance.main.port)
    POSTGRES_DB   = var.db_name

    # 평소에는 모든 LLM 호출이 Bedrock(gpt-oss), 실패하면 GPU EC2의 vLLM(EXAONE)으로 대체한다
    LLM_PROVIDER       = "vllm"
    LLM_MODEL          = "exaone3.5:7.8b"
    VLLM_URL           = "http://${aws_instance.gpu.private_ip}:8000"
    LLM_BEDROCK_REGION = var.bedrock_region
    LLM_BEDROCK_MODEL  = var.bedrock_model_id
    # Bedrock 실패로 엑사원 대체가 5분 안에 2번이면 꺼져 있는 GPU EC2를 앱이 켠다(TELME-204, iam.tf StartGpu 권한과 짝)
    VLLM_GPU_INSTANCE_ID = aws_instance.gpu.id

    # bge-m3는 같은 EC2 안의 TEI(CPU)가 맡는다. GPU가 꺼져 있어도 검색이 된다
    EMBEDDING_PROVIDER       = "tei"
    EMBEDDING_URL            = "http://127.0.0.1:8081"
    EMBEDDING_WARMUP_ENABLED = "true"

    ROUTING_REQUEST_INVENTORY_ENABLED = "false"

    # 매장 길찾기: 대중교통 출발지 → 첫 경로 좌표 도보를 추가 조회, 실제 경로 거리 50km 넘으면 안내 안 함
    KAKAO_ROUTE_TRANSIT_ORIGIN_WALK_ENABLED = "true"
    STORE_ROUTE_MAX_DISTANCE_METERS         = "50000"
    STORE_ROUTE_ORIGIN_WALK_MAX_STRAIGHT_DISTANCE_METERS = "3000"
    STORE_ROUTE_ORIGIN_WALK_REQUESTS_PER_MINUTE          = "30"
    # CloudFront→ALB 경유 요청의 실제 사용자 IP를 구분한다. SG도 같은 CloudFront prefix list만 허용한다.
    STORE_ROUTE_ORIGIN_WALK_TRUSTED_PROXIES = join(",", concat(
      [var.vpc_cidr],
      sort([for entry in data.aws_ec2_managed_prefix_list.cloudfront.entries : entry.cidr])
    ))

    # 시연용: 요금제 가입, 변경을 바로 적용하고 가입 당월, 월 1회 변경 제한을 생략
    TELME_SUBSCRIPTION_DEMO_MODE = "true"

    FRONTEND_URL = local.site_url
  }
}

resource "aws_ssm_parameter" "app_env" {
  for_each = local.app_env

  name  = "${local.env_prefix}/${each.key}"
  type  = "String"
  value = each.value
}

# CD가 새 이미지 태그를 여기에 쓰고 ASG Instance Refresh를 건다. Terraform은 처음 값만 넣는다
resource "aws_ssm_parameter" "image_tag" {
  name  = "/${var.name}/prod/deploy/image_tag"
  type  = "String"
  value = var.app_image_tag

  lifecycle {
    ignore_changes = [value]
  }
}

# 값은 JSON 하나로 넣는다. 키 이름이 그대로 환경 변수 이름이 된다
#   KAKAO_CLIENT_ID, KAKAO_CLIENT_SECRET, KAKAO_REST_API_KEY, GOOGLE_CLIENT_ID, GOOGLE_CLIENT_SECRET
resource "aws_secretsmanager_secret" "app" {
  name                    = "${var.name}/prod/app"
  description             = "OAuth·Kakao 키(JSON). 값은 콘솔에서 넣는다"
  recovery_window_in_days = 0
}
