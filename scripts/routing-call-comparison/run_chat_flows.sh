#!/usr/bin/env bash
# TELME-175 마지막 확인: 라우팅 호출 4개를 포함한 모든 LLM 호출을 gpt-oss로 두고 실제 채팅 API로
# TELME-174 대화 흐름 22개(되묻기·복합·FAQ+매장·멀티턴)를 3회 동시에 돈다. 앱 3개를 각자 DB로 띄운다.
#
# 준비
#   - bootJar로 만든 이 브랜치 jar (기본 build/libs/telme-0.0.1-SNAPSHOT.jar)
#   - Bedrock 라우터(기본 http://127.0.0.1:11435), 회색 구간 판정용 AWS 자격 증명(AWS_PROFILE)
#   - PostgreSQL(기본 5440)의 telme DB를 템플릿으로 복사한다. 컨테이너 이름은 PG_CONTAINER
#   - 대화 흐름 실행기 scripts/bedrock-switch-evaluation/run_flow_scenarios.py (TELME-174)
# 결과: scripts/routing-call-comparison/results/step5-chat-flows/flows-gptoss-{1,2,3}.json
#       판정(맞음·틀림과 이유)은 사람이 붙여 results.json.gz에 담는다
set -uo pipefail
cd "$(dirname "$0")/../.."

JAR=${JAR:-build/libs/telme-0.0.1-SNAPSHOT.jar}
ROUTER_URL=${ROUTER_URL:-http://127.0.0.1:11435}
PG_CONTAINER=${PG_CONTAINER:-telme-flow-postgres}
PG_PORT=${PG_PORT:-5440}
RUNNER=scripts/bedrock-switch-evaluation/run_flow_scenarios.py
OUT=scripts/routing-call-comparison/results/step5-chat-flows
PORTS=(18175 18176 18177)
mkdir -p "$OUT"

stop_port() {
  local pid
  pid=$(netstat -ano | grep -E ":$1 .*LISTEN" | awk '{print $NF}' | head -1)
  [ -n "$pid" ] && taskkill //PID "$pid" //F > /dev/null 2>&1
}

for n in 1 2 3; do
  db=telme_flow175_$n
  docker exec "$PG_CONTAINER" psql -U telme -d postgres -qc "DROP DATABASE IF EXISTS $db WITH (FORCE)" \
    -c "CREATE DATABASE $db TEMPLATE telme" 2>&1 | grep -v NOTICE
  LLM_BEDROCK_REGION=${LLM_BEDROCK_REGION:-ap-northeast-1} OLLAMA_URL="$ROUTER_URL" LLM_PROVIDER=ollama \
    POSTGRES_PORT="$PG_PORT" POSTGRES_DB=$db SERVER_PORT=${PORTS[$((n-1))]} java -jar "$JAR" > "$OUT/app-$n.log" 2>&1 &
done
for _ in $(seq 1 120); do
  [ "$(grep -alc 'Started TelmeApplication' "$OUT"/app-1.log "$OUT"/app-2.log "$OUT"/app-3.log 2>/dev/null | wc -l)" = 3 ] && break
  grep -aql "APPLICATION FAILED" "$OUT"/app-*.log 2>/dev/null && break
  sleep 2
done
if grep -aql "APPLICATION FAILED" "$OUT"/app-*.log 2>/dev/null; then
  echo "기동 실패"; for p in "${PORTS[@]}"; do stop_port "$p"; done; exit 1
fi

PIDS=()
for n in 1 2 3; do
  python -X utf8 "$RUNNER" --base-url http://localhost:${PORTS[$((n-1))]} --database telme_flow175_$n \
    --output "$OUT/flows-gptoss-$n.json" > "$OUT/flows-$n.txt" 2>&1 &
  PIDS+=($!)
done
for p in "${PIDS[@]}"; do wait "$p"; done
for p in "${PORTS[@]}"; do stop_port "$p"; done
rm -f "$OUT"/app-*.log "$OUT"/flows-*.txt
echo "완료"
