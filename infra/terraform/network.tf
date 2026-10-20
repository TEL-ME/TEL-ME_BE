# 앱, GPU EC2는 퍼블릭 서브넷에 두고 보안 그룹으로 막고, RDS만 프라이빗 서브넷에 둔다

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${var.name}-vpc" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${var.name}-igw" }
}

resource "aws_subnet" "public" {
  count = length(var.azs)

  vpc_id                  = aws_vpc.main.id
  availability_zone       = var.azs[count.index]
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index)
  map_public_ip_on_launch = true

  tags = { Name = "${var.name}-public-${substr(var.azs[count.index], -1, 1)}" }
}

# RDS는 서브넷 그룹에 가용 영역 2개가 있어야 만들 수 있다
resource "aws_subnet" "private" {
  count = length(var.azs)

  vpc_id            = aws_vpc.main.id
  availability_zone = var.azs[count.index]
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index + 10)

  tags = { Name = "${var.name}-private-${substr(var.azs[count.index], -1, 1)}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${var.name}-public" }
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# 기본 라우트(VPC 안)만
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${var.name}-private" }
}

resource "aws_route_table_association" "private" {
  count = length(aws_subnet.private)

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}

# ECR 이미지 레이어(S3)를 VPC 안 경로로 받는다
resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.main.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = [aws_route_table.public.id]

  tags = { Name = "${var.name}-s3" }
}

# ---------- 보안 그룹 ----------
# 인터넷에서 들어오는 문은 ALB 80 하나, CloudFront 원본용 IP 대역에서 온 요청만 받는다
# 비밀 헤더 확인은 ALB 리스너 규칙(app.tf)이 한다. SSH는 어디에도 열지 않는다(SSM Session Manager 사용)

data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "alb" {
  name        = "${var.name}-alb"
  description = "ALB: HTTP 80 from CloudFront only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.name}-alb" }
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_cloudfront" {
  security_group_id = aws_security_group.alb.id
  description       = "HTTP from CloudFront origin-facing"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
  prefix_list_id    = data.aws_ec2_managed_prefix_list.cloudfront.id
}

resource "aws_vpc_security_group_egress_rule" "alb_to_app" {
  security_group_id            = aws_security_group.alb.id
  description                  = "To app 8080"
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  referenced_security_group_id = aws_security_group.app.id
}

resource "aws_security_group" "app" {
  name        = "${var.name}-app"
  description = "App EC2: 8080 from ALB only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.name}-app" }
}

resource "aws_vpc_security_group_ingress_rule" "app_from_alb" {
  security_group_id            = aws_security_group.app.id
  description                  = "Spring Boot from ALB"
  ip_protocol                  = "tcp"
  from_port                    = 8080
  to_port                      = 8080
  referenced_security_group_id = aws_security_group.alb.id
}

# Bedrock, Kakao, OAuth, ECR, SSM, GPU(8000), RDS로 나간다
resource "aws_vpc_security_group_egress_rule" "app_all" {
  security_group_id = aws_security_group.app.id
  description       = "All outbound"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_security_group" "rds" {
  name        = "${var.name}-rds"
  description = "RDS: 5432 from app only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.name}-rds" }
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_app" {
  security_group_id            = aws_security_group.rds.id
  description                  = "PostgreSQL from app"
  ip_protocol                  = "tcp"
  from_port                    = 5432
  to_port                      = 5432
  referenced_security_group_id = aws_security_group.app.id
}

resource "aws_security_group" "gpu" {
  name        = "${var.name}-gpu"
  description = "GPU EC2: vLLM 8000 from app only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${var.name}-gpu" }
}

resource "aws_vpc_security_group_ingress_rule" "gpu_from_app" {
  security_group_id            = aws_security_group.gpu.id
  description                  = "vLLM from app"
  ip_protocol                  = "tcp"
  from_port                    = 8000
  to_port                      = 8000
  referenced_security_group_id = aws_security_group.app.id
}

# 모델 다운로드(Hugging Face), Docker 이미지, SSM으로 나간다
resource "aws_vpc_security_group_egress_rule" "gpu_all" {
  security_group_id = aws_security_group.gpu.id
  description       = "All outbound"
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}
