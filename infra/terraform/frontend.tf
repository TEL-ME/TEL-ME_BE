# CloudFront 하나로 www.telme.site(us-east-1 ACM 인증서)를 연다. 도메인 변수를 비우면 *.cloudfront.net 기본 주소, 인증서로 연다.
# 화면 파일은 S3, /api·로그인 경로는 ALB로 보낸다. 같은 주소라 CORS·쿠키 문제가 없다.
# 지웠다 다시 만들면 CloudFront 주소가 바뀌어 가비아 www CNAME을 고쳐야 한다(도메인 없이 쓰면 OAuth, 카카오맵 등록도 다시)

resource "aws_s3_bucket" "frontend" {
  bucket        = "${var.name}-frontend-${data.aws_caller_identity.current.account_id}"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "frontend" {
  bucket                  = aws_s3_bucket.frontend.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_cloudfront_origin_access_control" "frontend" {
  name                              = "${var.name}-frontend"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

data "aws_iam_policy_document" "frontend_bucket" {
  statement {
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.frontend.arn}/*"]
    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.main.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "frontend" {
  bucket = aws_s3_bucket.frontend.id
  policy = data.aws_iam_policy_document.frontend_bucket.json
}

data "aws_cloudfront_cache_policy" "optimized" {
  name = "Managed-CachingOptimized"
}

data "aws_cloudfront_cache_policy" "disabled" {
  name = "Managed-CachingDisabled"
}

# Host만 빼고 헤더·쿠키·쿼리를 모두 ALB로 넘긴다
data "aws_cloudfront_origin_request_policy" "all_viewer_except_host" {
  name = "Managed-AllViewerExceptHostHeader"
}

# SPA: 확장자가 없는 경로(/chat, /stores/3 등)는 index.html로 바꾼다.
# 사용자 지정 오류 응답(403·404 → index.html)은 API 오류까지 바꿔 버려서 쓰지 않는다
resource "aws_cloudfront_function" "spa" {
  name    = "${var.name}-spa-rewrite"
  runtime = "cloudfront-js-2.0"
  publish = true
  code    = <<-EOF
    function handler(event) {
      var request = event.request;
      if (!request.uri.includes('.')) {
        request.uri = '/index.html';
      }
      return request;
    }
  EOF
}

locals {
  s3_origin_id  = "s3-frontend"
  alb_origin_id = "alb-api"
  api_paths     = ["/api/*", "/oauth2/*", "/login/oauth2/*"]

  # 도메인이 있으면 그 주소, 없으면 CloudFront 기본 주소
  site_url = length(var.domain_names) > 0 ? "https://${var.domain_names[0]}" : "https://${aws_cloudfront_distribution.main.domain_name}"
}

resource "aws_cloudfront_distribution" "main" {
  enabled             = true
  comment             = "${var.name} frontend + api"
  default_root_object = "index.html"
  price_class         = "PriceClass_200"
  http_version        = "http2and3"
  aliases             = var.domain_names

  origin {
    origin_id                = local.s3_origin_id
    domain_name              = aws_s3_bucket.frontend.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.frontend.id
  }

  origin {
    origin_id   = local.alb_origin_id
    domain_name = aws_lb.main.dns_name

    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
      # SSE 답변이 길게 이어져도 끊기지 않게 최댓값(60초)
      origin_read_timeout      = 60
      origin_keepalive_timeout = 60
    }

    custom_header {
      name  = local.origin_secret_header
      value = random_password.origin_secret.result
    }
  }

  default_cache_behavior {
    target_origin_id       = local.s3_origin_id
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.optimized.id
    compress               = true

    function_association {
      event_type   = "viewer-request"
      function_arn = aws_cloudfront_function.spa.arn
    }
  }

  dynamic "ordered_cache_behavior" {
    for_each = local.api_paths
    content {
      path_pattern             = ordered_cache_behavior.value
      target_origin_id         = local.alb_origin_id
      viewer_protocol_policy   = "redirect-to-https"
      allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
      cached_methods           = ["GET", "HEAD"]
      cache_policy_id          = data.aws_cloudfront_cache_policy.disabled.id
      origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer_except_host.id
      # SSE는 압축하면 글자가 모였다가 한 번에 온다
      compress = false
    }
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # 도메인을 붙이면 ACM 인증서(SNI), 없으면 *.cloudfront.net 기본 인증서
  viewer_certificate {
    cloudfront_default_certificate = var.acm_certificate_arn == ""
    acm_certificate_arn            = var.acm_certificate_arn == "" ? null : var.acm_certificate_arn
    ssl_support_method             = var.acm_certificate_arn == "" ? null : "sni-only"
    minimum_protocol_version       = var.acm_certificate_arn == "" ? "TLSv1" : "TLSv1.2_2021"
  }
}
