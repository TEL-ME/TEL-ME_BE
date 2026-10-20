# 예산 알림은 콘솔에서 만들었고, 주 1회 GPU 점검은 사람이 한다(둘 다 여기 없음)

resource "aws_cloudwatch_log_group" "app" {
  name              = "/${var.name}/prod/app"
  retention_in_days = var.log_retention_days
}

resource "aws_cloudwatch_log_group" "gpu" {
  name              = "/${var.name}/prod/gpu"
  retention_in_days = var.log_retention_days
}

resource "aws_sns_topic" "alarms" {
  name = "${var.name}-alarms"
}

resource "aws_sns_topic_subscription" "email" {
  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = var.alarm_email
}

resource "aws_cloudwatch_metric_alarm" "alb_5xx" {
  alarm_name          = "${var.name}-app-5xx"
  alarm_description   = "앱이 5분 동안 5xx를 10번 넘게 냄"
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_Target_5XX_Count"
  dimensions          = { LoadBalancer = aws_lb.main.arn_suffix }
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 10
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
}

resource "aws_cloudwatch_metric_alarm" "no_healthy_app" {
  count = var.app_instance_count > 0 ? 1 : 0

  alarm_name        = "${var.name}-app-unhealthy"
  alarm_description = "정상인 앱 서버가 5분 넘게 0대"
  namespace         = "AWS/ApplicationELB"
  metric_name       = "HealthyHostCount"
  dimensions = {
    LoadBalancer = aws_lb.main.arn_suffix
    TargetGroup  = aws_lb_target_group.app.arn_suffix
  }
  statistic           = "Minimum"
  period              = 60
  evaluation_periods  = 5
  threshold           = 1
  comparison_operator = "LessThanThreshold"
  treat_missing_data  = "breaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
}

# 꺼져 있으면 지표가 없다. 5분 지표가 24번(2시간) 연속 있으면 = 2시간 넘게 켜져 있으면 알린다
resource "aws_cloudwatch_metric_alarm" "gpu_running_long" {
  alarm_name          = "${var.name}-gpu-running-2h"
  alarm_description   = "GPU EC2가 2시간 넘게 켜져 있음. 자동 중지가 안 됐는지 확인"
  namespace           = "AWS/EC2"
  metric_name         = "CPUUtilization"
  dimensions          = { InstanceId = aws_instance.gpu.id }
  statistic           = "SampleCount"
  period              = 300
  evaluation_periods  = 24
  threshold           = 0
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
}

# Bedrock 호출이 실패해 엑사원(vLLM)으로 대체된 횟수. 앱 코드 없이 로그(FallbackLlmClient의 "[LLM 대체]")에서 센다
resource "aws_cloudwatch_log_metric_filter" "bedrock_fallback" {
  name           = "${var.name}-bedrock-fallback"
  log_group_name = aws_cloudwatch_log_group.app.name
  pattern        = "\"[LLM 대체]\""

  metric_transformation {
    namespace     = "TelMe"
    name          = "BedrockFallbackCount"
    value         = "1"
    default_value = "0"
  }
}

# 대체가 한 번이라도 일어나면 사람에게 바로 알린다. GPU는 비용이 들어 앱이 5분 안에 대체 2번일 때 켠다(TELME-204)
resource "aws_cloudwatch_metric_alarm" "bedrock_fallback" {
  alarm_name          = "${var.name}-bedrock-fallback"
  alarm_description   = "Bedrock 호출이 실패해 엑사원으로 대체됨. Bedrock 상태와 GPU 기동을 확인"
  namespace           = "TelMe"
  metric_name         = aws_cloudwatch_log_metric_filter.bedrock_fallback.metric_transformation[0].name
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
}
