#!/bin/bash
# 시나리오 여러 개를 차례로 돈다. SSH가 끊겨도 계속 돌도록 분리해서 띄운다.
# 사용: GPU_HOST=... DB_HOST=... nohup setsid ./series.sh vllm "S1 1 100" "S1 5 100" "S0 1 100" > series-vllm.log 2>&1 < /dev/null &
cd "$(dirname "$0")"
CONFIG=$1; shift
for spec in "$@"; do
  set -- $spec
  echo "START $CONFIG $spec $(date -Iseconds)"
  ./run.sh "$CONFIG" "$1" "$2" "$3" "${4:-}" | tail -1
done
echo "SERIES DONE $CONFIG $(date -Iseconds)"
