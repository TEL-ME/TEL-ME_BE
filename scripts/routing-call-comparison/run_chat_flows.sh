#!/usr/bin/env bash
# TELME-175 마지막 확인: 라우팅 호출 4개를 포함한 모든 LLM 호출을 gpt-oss로 두고 실제 채팅 API로
# TELME-174 대화 흐름 22개(되묻기·복합·FAQ+매장·멀티턴)를 3회 동시에 돈다. 앱 3개를 각자 DB로 띄운다.
#
# 준비
#   - bootJar로 만든 이 브랜치 jar (기본 build/libs/telme-0.0.1-SNAPSHOT.jar)
#   - Bedrock 라우터(기본 http://127.0.0.1:11435), 회색 구간 판정용 AWS 자격 증명(AWS_PROFILE)
#   - 요청 개수 판정을 끈 구성은 ROUTING_REQUEST_INVENTORY_ENABLED=false를 붙여 실행한다(앱에 그대로 넘어간다)
#   - PostgreSQL(기본 5440)의 telme DB를 템플릿으로 복사한다. 컨테이너 이름은 PG_CONTAINER
#   - 대화 흐름 실행기 scripts/bedrock-switch-evaluation/run_flow_scenarios.py (TELME-174, #159). 이 브랜치에 없으면 RUNNER로 경로를 준다
# 결과: scripts/routing-call-comparison/results/step5-chat-flows/flows-gptoss-{1,2,3}.json
#       판정(맞음·틀림과 이유)은 사람이 붙여 results.json.gz에 담는다
set -uo pipefail
cd "$(dirname "$0")/../.."

JAR=${JAR:-build/libs/telme-0.0.1-SNAPSHOT.jar}
ROUTER_URL=${ROUTER_URL:-http://127.0.0.1:11435}
PG_CONTAINER=${PG_CONTAINER:-telme-flow-postgres}
PG_PORT=${PG_PORT:-5440}
RUNNER=${RUNNER:-scripts/bedrock-switch-evaluation/run_flow_scenarios.py}
OUT=scripts/routing-call-comparison/results/step5-chat-flows
PORTS=(18175 18176 18177)
mkdir -p "$OUT"

APP_PIDS=()
PIDS=()
# 이번 실행이 띄운 앱과 실행기만 끈다. 실패하거나 중단돼도 EXIT에서 정리된다
cleanup() {
  for pid in ${APP_PIDS[*]:-} ${PIDS[*]:-}; do kill "$pid" 2> /dev/null; done
  wait 2> /dev/null
}
trap cleanup EXIT

for n in 1 2 3; do
  db=telme_flow175_$n
  docker exec "$PG_CONTAINER" psql -U telme -d postgres -qc "DROP DATABASE IF EXISTS $db WITH (FORCE)" \
    -c "CREATE DATABASE $db TEMPLATE telme" 2>&1 | grep -v NOTICE
  LLM_BEDROCK_REGION=${LLM_BEDROCK_REGION:-ap-northeast-1} OLLAMA_URL="$ROUTER_URL" LLM_PROVIDER=ollama \
    POSTGRES_PORT="$PG_PORT" POSTGRES_DB=$db SERVER_PORT=${PORTS[$((n-1))]} java -jar "$JAR" > "$OUT/app-$n.log" 2>&1 &
  APP_PIDS+=($!)
done
for _ in $(seq 1 120); do
  [ "$(grep -alc 'Started TelmeApplication' "$OUT"/app-1.log "$OUT"/app-2.log "$OUT"/app-3.log 2>/dev/null | wc -l)" = 3 ] && break
  grep -aql "APPLICATION FAILED" "$OUT"/app-*.log 2>/dev/null && break
  sleep 2
done
if grep -aql "APPLICATION FAILED" "$OUT"/app-*.log 2>/dev/null; then
  echo "기동 실패"; exit 1
fi

# 이전 실행 결과를 이번 결과로 착각하지 않도록 먼저 지운다
rm -f "$OUT"/flows-gptoss-*.json
for n in 1 2 3; do
  python -X utf8 "$RUNNER" --base-url http://localhost:${PORTS[$((n-1))]} --database telme_flow175_$n \
    --output "$OUT/flows-gptoss-$n.json" > "$OUT/flows-$n.txt" 2>&1 &
  PIDS+=($!)
done
FAILED=0
for i in "${!PIDS[@]}"; do
  if ! wait "${PIDS[$i]}"; then
    echo "실행기 $((i+1)) 실패: $OUT/flows-$((i+1)).txt"; FAILED=1
  fi
  if [ ! -s "$OUT/flows-gptoss-$((i+1)).json" ]; then
    echo "결과 없음: $OUT/flows-gptoss-$((i+1)).json"; FAILED=1
  fi
done
cleanup
# 실패하면 앱·실행기 로그를 남기고 비정상 종료한다
if [ "$FAILED" -ne 0 ]; then
  echo "실패. 로그를 남겼습니다: $OUT/app-*.log, $OUT/flows-*.txt"
  exit 1
fi
rm -f "$OUT"/app-*.log "$OUT"/flows-*.txt
echo "완료"
