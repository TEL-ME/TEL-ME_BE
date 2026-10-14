#!/bin/bash
# vLLM EXAONE 3.5 7.8B AWQ 서버용 cloud-init (Deep Learning Base OSS Nvidia Driver GPU AMI, Ubuntu 22.04, g6.xlarge).
# DLAMI가 루트 볼륨을 이미 약 54GB 쓰므로 vLLM 이미지(약 30GB)와 모델은 인스턴스 NVMe(/opt/dlami/nvme)에 받는다.
set -x
exec > /var/log/vllm-bootstrap.log 2>&1

# 안전장치: 120분 뒤 자동 종료 (--instance-initiated-shutdown-behavior terminate와 함께 쓰면 완전 종료)
shutdown -h +120 "telme judge-vllm auto shutdown"

# docker의 data-root만 바꾸면 이미지는 containerd가 관리해 여전히 루트 볼륨에 쌓인다. containerd와 docker 저장소를 둘 다 NVMe로 옮긴다
NVME=/opt/dlami/nvme
systemctl stop docker docker.socket containerd 2>/dev/null || true
sleep 3
for NAME in containerd docker; do
  mkdir -p "$NVME/$NAME"
  if [ -d "/var/lib/$NAME" ] && [ ! -L "/var/lib/$NAME" ]; then
    rm -rf "/var/lib/$NAME"
    ln -s "$NVME/$NAME" "/var/lib/$NAME"
  fi
done
systemctl start containerd
systemctl start docker
sleep 5
ls -la /var/lib/containerd /var/lib/docker
df -h / "$NVME"

HF_CACHE="$NVME/hfcache"
mkdir -p "$HF_CACHE"

# 이미지 digest와 실행 옵션을 고정한다 (2026-10-10 모델 비교 실험과 같음)
docker run -d \
  --name vllm-bench \
  --restart unless-stopped \
  --gpus all \
  -p 8000:8000 \
  -v "$HF_CACHE:/root/.cache/huggingface" \
  vllm/vllm-openai@sha256:c1c9f6fd5c109ba7f0546a59f5b2f15fb87f64c77782e90a27b648b42a8e67c3 \
  --model LGAI-EXAONE/EXAONE-3.5-7.8B-Instruct-AWQ \
  --served-model-name ab-chat \
  --quantization awq \
  --dtype half \
  --trust-remote-code \
  --max-model-len 8192 \
  --gpu-memory-utilization 0.90 \
  --max-num-seqs 8 \
  --seed 0

echo "bootstrap done at $(date)" > /home/ubuntu/bootstrap-done
