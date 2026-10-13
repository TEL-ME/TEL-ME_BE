#!/usr/bin/env bash
# vLLM이 필요한 구성을 측정한다: g6.xlarge 스팟 기동(용량 부족이면 1분 간격 재시도) → vLLM 준비 확인 → 구성 측정 → 인스턴스 종료 → 스팟 요청 취소
# 측정 중 오류가 나도 종료는 반드시 실행한다(trap).
#
# 사용: run_vllm_configs.sh "<구성 이름> <판정기 true|false> <judge_router 인자...>" ["..."]
#   예) QA_T=0.71 QO_T=0.80 run_vllm_configs.sh "B2 true --chat vllm --judge chat" "C2 true --chat vllm --judge bedrock"
#   --vllm-url은 이 스크립트가 붙인다.
#
# 필요한 환경 변수: AWS_PROFILE, AWS_REGION, AMI_ID, KEY_NAME, SUBNET_ID, SG_ID (보안 그룹은 이 PC IP에만 8000 허용)
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
: "${AWS_PROFILE:?}" "${AWS_REGION:?}" "${AMI_ID:?}" "${KEY_NAME:?}" "${SUBNET_ID:?}" "${SG_ID:?}"
export AWS_PROFILE AWS_REGION
UD="fileb://$(cygpath -m "$HERE/user-data-vllm-exaone.sh" 2>/dev/null || echo "$HERE/user-data-vllm-exaone.sh")"

ID=""
for attempt in $(seq 1 15); do
  # Git Bash가 --block-device-mappings 등의 경로 비슷한 인자를 바꾸지 않도록 이 명령에만 적용한다
  OUT=$(MSYS2_ARG_CONV_EXCL='*' aws ec2 run-instances --image-id "$AMI_ID" --instance-type g6.xlarge --key-name "$KEY_NAME" \
    --network-interfaces "DeviceIndex=0,SubnetId=$SUBNET_ID,Groups=$SG_ID,AssociatePublicIpAddress=true,DeleteOnTermination=true" \
    --instance-market-options 'MarketType=spot,SpotOptions={SpotInstanceType=one-time,InstanceInterruptionBehavior=terminate}' \
    --instance-initiated-shutdown-behavior terminate \
    --block-device-mappings "DeviceName=/dev/sda1,Ebs={VolumeSize=100,VolumeType=gp3,DeleteOnTermination=true}" \
    --user-data "$UD" \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=telme-judge-vllm}]" \
                         "ResourceType=volume,Tags=[{Key=Name,Value=telme-judge-vllm}]" \
    --query "Instances[0].InstanceId" --output text 2>&1)
  if [[ "$OUT" == i-* ]]; then ID="$OUT"; echo "시도 $attempt 성공: $ID $(date +%H:%M:%S)"; break; fi
  echo "시도 $attempt 실패 $(date +%H:%M:%S): $(echo "$OUT" | grep -o 'InsufficientInstanceCapacity\|[A-Za-z]*Exception' | head -1)"
  sleep 60
done
[ -z "$ID" ] && { echo "기동 실패: 15회 모두 실패"; exit 1; }

cleanup() {
  aws ec2 terminate-instances --instance-ids "$ID" >/dev/null
  aws ec2 wait instance-terminated --instance-ids "$ID"
  echo "종료 확인: $(aws ec2 describe-instances --instance-ids "$ID" --query 'Reservations[0].Instances[0].[State.Name,LaunchTime]' --output text) $(date -u +%H:%M:%SZ)"
  # one-time 스팟 요청은 인스턴스를 종료해도 한동안 active로 남아 다음 기동을 막는다
  for SIR in $(aws ec2 describe-spot-instance-requests --query "SpotInstanceRequests[?InstanceId=='$ID'].SpotInstanceRequestId" --output text); do
    aws ec2 cancel-spot-instance-requests --spot-instance-request-ids "$SIR" --query "CancelledSpotInstanceRequests[0].State" --output text
  done
  echo "남은 실행 중 인스턴스: [$(aws ec2 describe-instances --filters Name=instance-state-name,Values=pending,running --query 'Reservations[].Instances[].InstanceId' --output text)]"
}
trap cleanup EXIT

aws ec2 wait instance-running --instance-ids "$ID"
IP=$(aws ec2 describe-instances --instance-ids "$ID" --query "Reservations[0].Instances[0].PublicIpAddress" --output text)
READY=""
for _ in $(seq 1 90); do
  CODE=$(curl -s -m 5 -o "$HERE/.health.tmp" -w "%{http_code}" "http://$IP:8000/health" 2>/dev/null)
  [ "$CODE" = "200" ] && { READY=1; echo "vLLM 준비 $(date +%H:%M:%S)"; break; }
  sleep 10
done
rm -f "$HERE/.health.tmp"
[ -z "$READY" ] && { echo "vLLM 준비 실패 (15분 초과)"; exit 1; }

for CONFIG in "$@"; do
  # shellcheck disable=SC2086
  bash "$HERE/run_config.sh" $CONFIG --vllm-url "http://$IP:8000" 2>&1 | grep -v NOTICE
done
