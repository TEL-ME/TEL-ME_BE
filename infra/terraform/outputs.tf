output "site_url" {
  description = "서비스 주소. Google·Kakao 로그인 리다이렉트 URI와 카카오맵 JS SDK 도메인에 등록한다"
  value       = local.site_url
}

output "oauth_redirect_uris" {
  value = [
    "${local.site_url}/login/oauth2/code/google",
    "${local.site_url}/login/oauth2/code/kakao",
  ]
}

output "cloudfront_domain_name" {
  description = "가비아 DNS에서 www CNAME의 값으로 넣는다"
  value       = aws_cloudfront_distribution.main.domain_name
}

output "cloudfront_distribution_id" {
  value = aws_cloudfront_distribution.main.id
}

output "frontend_bucket" {
  value = aws_s3_bucket.frontend.bucket
}

output "ecr_repository_url" {
  value = aws_ecr_repository.backend.repository_url
}

output "image_tag_parameter" {
  value = aws_ssm_parameter.image_tag.name
}

output "asg_name" {
  value = aws_autoscaling_group.app.name
}

output "app_secret_arn" {
  description = "OAuth·Kakao 키 JSON을 넣을 시크릿"
  value       = aws_secretsmanager_secret.app.arn
}

output "db_endpoint" {
  value = aws_db_instance.main.address
}

output "gpu_instance_id" {
  value = aws_instance.gpu.id
}

output "gpu_private_ip" {
  value = aws_instance.gpu.private_ip
}

output "github_deploy_role_arn" {
  description = "GitHub Actions의 aws-actions/configure-aws-credentials role-to-assume"
  value       = aws_iam_role.github_deploy.arn
}
