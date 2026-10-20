# Bedrock 장애 때만 켜는 EXAONE(vLLM) 서버. apply가 끝나면 중지 상태로
# 모델 다운로드는 사람이 한 번 켜서 한다(README의 "GPU 처음 준비")

data "aws_ssm_parameter" "dlami" {
  name = "/aws/service/deeplearning/ami/x86_64/base-oss-nvidia-driver-gpu-ubuntu-22.04/latest/ami-id"
}

resource "aws_instance" "gpu" {
  ami                    = data.aws_ssm_parameter.dlami.value
  instance_type          = var.gpu_instance_type
  subnet_id              = aws_subnet.public[0].id
  vpc_security_group_ids = [aws_security_group.gpu.id]
  iam_instance_profile   = aws_iam_instance_profile.gpu.name

  # 퍼블릭 IP는 켜진 동안만 붙고 요금도 그때만 나간다. 사설 IP는 중지, 시작해도 그대로라 VLLM_URL이 바뀌지 않는다
  associate_public_ip_address = true

  # 안에서 shutdown을 하면 종료가 아니라 중지가 된다(유휴 스크립트가 이걸로 끈다)
  instance_initiated_shutdown_behavior = "stop"

  metadata_options {
    http_tokens = "required"
  }

  root_block_device {
    volume_size = var.gpu_volume_size
    volume_type = "gp3"
    encrypted   = true
  }

  # cloud-boothook이라 부팅할 때마다 실행된다. apply 직후 바로 중지돼 첫 부팅이 끊겨도 다음 부팅에 다시 깔린다
  user_data = templatefile("${path.module}/user_data/gpu.sh", {
    region    = var.region
    log_group = aws_cloudwatch_log_group.gpu.name
  })

  tags = { Name = "${var.name}-gpu" }

  # 꺼 두면 AWS가 공인 IP를 회수해 associate_public_ip_address가 false로 읽힌다.
  # 무시하지 않으면 apply마다 GPU를 지우고 새로 만들어 받아 둔 모델(EBS)까지 사라진다
  lifecycle {
    ignore_changes = [ami, associate_public_ip_address]
  }
}

resource "aws_ec2_instance_state" "gpu" {
  instance_id = aws_instance.gpu.id
  state       = "stopped"

  # 장애 때 앱이 켠 상태를 다음 apply가 다시 끄지 않게 한다
  lifecycle {
    ignore_changes = [state]
  }
}
