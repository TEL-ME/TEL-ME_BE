# CloudFront → ALB(HTTP 80) → 앱 EC2(8080) 도메인이 없어 ALB에 붙일 인증서를 받을 수 없으므로
# 이 구간은 HTTP다. ALB는 CloudFront가 붙인 비밀 헤더가 맞는 요청만 넘기고 나머지는 403으로 막는다

resource "random_password" "origin_secret" {
  length  = 40
  special = false
}

locals {
  origin_secret_header = "X-Origin-Verify"
}

resource "aws_lb" "main" {
  name               = "${var.name}-alb"
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  # SSE 응답 사이 빈 시간이 이보다 길면 ALB가 연결을 끊는다(CloudFront 응답 대기 60초보다 길게)
  idle_timeout = 120
}

resource "aws_lb_target_group" "app" {
  name     = "${var.name}-app"
  port     = 8080
  protocol = "HTTP"
  vpc_id   = aws_vpc.main.id

  # SSE가 끝날 시간을 준다
  deregistration_delay = 120

  health_check {
    path                = "/actuator/health"
    matcher             = "200"
    interval            = 15
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 4
  }

  # 대화 상태와 SSE 연결이 서버 메모리에 있어서 같은 사용자를 같은 서버로 보낸다
  stickiness {
    type            = "lb_cookie"
    cookie_duration = 86400
    enabled         = true
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "text/plain"
      message_body = "Forbidden"
      status_code  = "403"
    }
  }
}

resource "aws_lb_listener_rule" "from_cloudfront" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 1

  condition {
    http_header {
      http_header_name = local.origin_secret_header
      values           = [random_password.origin_secret.result]
    }
  }

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

# ---------- 앱 EC2 (ASG) ----------

data "aws_ssm_parameter" "al2023" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

resource "aws_launch_template" "app" {
  name_prefix   = "${var.name}-app-"
  image_id      = data.aws_ssm_parameter.al2023.value
  instance_type = var.app_instance_type

  iam_instance_profile {
    arn = aws_iam_instance_profile.app.arn
  }

  vpc_security_group_ids = [aws_security_group.app.id]

  metadata_options {
    http_tokens                 = "required"
    http_put_response_hop_limit = 2
  }

  block_device_mappings {
    device_name = "/dev/xvda"
    ebs {
      volume_size           = 30
      volume_type           = "gp3"
      encrypted             = true
      delete_on_termination = true
    }
  }

  # t3는 CPU 크레딧이 떨어지면 느려진다. unlimited면 초과분만 과금하고 느려지지 않는다
  credit_specification {
    cpu_credits = "unlimited"
  }

  user_data = base64encode(templatefile("${path.module}/user_data/app.sh", {
    region          = var.region
    registry        = split("/", aws_ecr_repository.backend.repository_url)[0]
    repository_url  = aws_ecr_repository.backend.repository_url
    env_prefix      = local.env_prefix
    image_tag_param = aws_ssm_parameter.image_tag.name
    app_secret_arn  = aws_secretsmanager_secret.app.arn
    db_secret_arn   = aws_db_instance.main.master_user_secret[0].secret_arn
    tei_image       = var.tei_image
    log_group       = aws_cloudwatch_log_group.app.name
  }))

  # provider default_tags는 시작 템플릿 자체에만 붙고 ASG가 띄우는 EC2, EBS에는 붙지 않는다.
  # 팀원 SSM 접속 정책(infra/iam)이 Project=telme 태그로 대상을 고르므로 여기서 직접 붙인다
  tag_specifications {
    resource_type = "instance"
    tags          = merge(data.aws_default_tags.current.tags, { Name = "${var.name}-app" })
  }

  tag_specifications {
    resource_type = "volume"
    tags          = merge(data.aws_default_tags.current.tags, { Name = "${var.name}-app" })
  }

  # 새 AMI가 나올 때마다 서버가 교체되지 않게 한다
  lifecycle {
    ignore_changes = [image_id]
  }
}

resource "aws_autoscaling_group" "app" {
  name                = "${var.name}-app"
  vpc_zone_identifier = aws_subnet.public[*].id
  target_group_arns   = [aws_lb_target_group.app.arn]

  min_size         = var.app_instance_count
  desired_capacity = var.app_instance_count
  max_size         = var.app_max_size

  health_check_type = "ELB"
  # 첫 부팅에 Docker 설치, TEI 모델(bge-m3) 다운로드, 앱 기동까지 기다린다
  health_check_grace_period = 600

  launch_template {
    id      = aws_launch_template.app.id
    version = aws_launch_template.app.latest_version
  }

  # 시작 템플릿이 바뀌면 새 서버를 먼저 띄우고 헬스 체크가 통과한 뒤 옛 서버를 내린다
  instance_refresh {
    strategy = "Rolling"
    preferences {
      min_healthy_percentage = 100
      max_healthy_percentage = 200
      instance_warmup        = 600
    }
  }

  tag {
    key                 = "Name"
    value               = "${var.name}-app"
    propagate_at_launch = true
  }

  # ASG 자원에는 default_tags가 붙지 않아 따로 둔다. EC2 태그는 시작 템플릿이 붙이므로 전파하지 않는다
  dynamic "tag" {
    for_each = data.aws_default_tags.current.tags
    content {
      key                 = tag.key
      value               = tag.value
      propagate_at_launch = false
    }
  }
}

data "aws_default_tags" "current" {}

resource "aws_autoscaling_policy" "cpu" {
  name                   = "${var.name}-app-cpu"
  autoscaling_group_name = aws_autoscaling_group.app.name
  policy_type            = "TargetTrackingScaling"

  target_tracking_configuration {
    predefined_metric_specification {
      predefined_metric_type = "ASGAverageCPUUtilization"
    }
    target_value = 60
  }
}
