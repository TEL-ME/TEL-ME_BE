#cloud-boothook
#!/bin/bash
# GPU EC2 부팅마다 실행된다(파일만 쓰므로 여러 번 돌아도 같다)
#   /opt/telme/vllm.sh            vLLM(EXAONE AWQ) 컨테이너를 띄운다. 처음 한 번 사람이 실행하면 이후엔 Docker가 다시 띄운다
#   /opt/telme/idle-shutdown.sh   5분마다 cron. 30분 동안 요청이 없으면 shutdown(= 중지)
#   /etc/telme/keep-running       이 파일이 있으면 자동 중지를 하지 않는다(모델 받기·점검할 때)
mkdir -p /opt/telme /etc/telme

cat > /opt/telme/vllm.sh <<'SCRIPT'
#!/bin/bash
# served-model-name은 앱의 LLM_MODEL(exaone3.5:7.8b)과 같아야 한다.
# 이미지·옵션은 TELME-187 scripts/serving-comparison/gpu-compose.yml의 vllm과 같다. TEI는 앱 EC2로 옮겨서 GPU를 다 쓴다
set -euo pipefail
TOKEN=$(curl -sX PUT http://169.254.169.254/latest/api/token -H "X-aws-ec2-metadata-token-ttl-seconds: 300")
INSTANCE_ID=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/instance-id)
docker rm -f telme-vllm >/dev/null 2>&1 || true
docker run -d --name telme-vllm --restart unless-stopped \
  --gpus all --ipc host \
  -p 8000:8000 \
  -v /opt/telme/hf-cache:/root/.cache/huggingface \
  --log-driver awslogs --log-opt awslogs-region=${region} \
  --log-opt awslogs-group=${log_group} --log-opt awslogs-stream="vllm-$INSTANCE_ID" \
  vllm/vllm-openai@sha256:c1c9f6fd5c109ba7f0546a59f5b2f15fb87f64c77782e90a27b648b42a8e67c3 \
  --model LGAI-EXAONE/EXAONE-3.5-7.8B-Instruct-AWQ \
  --served-model-name exaone3.5:7.8b \
  --quantization awq \
  --dtype half \
  --trust-remote-code \
  --max-model-len 8192 \
  --gpu-memory-utilization 0.90 \
  --max-num-seqs 8 \
  --seed 0
SCRIPT

cat > /opt/telme/idle-shutdown.sh <<'SCRIPT'
#!/bin/bash
# 처리한 요청 수가 30분 동안 그대로이고 처리 중인 요청도 없으면 끈다.
# /run은 부팅마다 비워지므로 켠 뒤 최소 30분은 켜져 있다
set -u
[ -f /etc/telme/keep-running ] && exit 0

STATE=/run/telme-idle
mkdir -p "$STATE"
NOW=$(date +%s)
[ -f "$STATE/last_active" ] || echo "$NOW" > "$STATE/last_active"

METRICS=$(curl -sf --max-time 5 http://127.0.0.1:8000/metrics || true)
TOTAL=$(echo "$METRICS" | awk '/^vllm:request_success_total/ {s+=$2} END {printf "%d", s}')
BUSY=$(echo "$METRICS" | awk '/^vllm:num_requests_(running|waiting)/ {s+=$2} END {printf "%d", s}')
PREV=$(cat "$STATE/last_total" 2>/dev/null || echo -1)

if [ "$TOTAL" != "$PREV" ] || [ "$BUSY" -gt 0 ]; then
  echo "$NOW" > "$STATE/last_active"
  echo "$TOTAL" > "$STATE/last_total"
fi

IDLE=$(( NOW - $(cat "$STATE/last_active") ))
if [ "$IDLE" -ge 1800 ]; then
  logger -t telme-idle "30분 동안 요청이 없어 중지한다"
  shutdown -h now
fi
SCRIPT

chmod 755 /opt/telme/vllm.sh /opt/telme/idle-shutdown.sh
echo '*/5 * * * * root /opt/telme/idle-shutdown.sh' > /etc/cron.d/telme-idle
chmod 644 /etc/cron.d/telme-idle
