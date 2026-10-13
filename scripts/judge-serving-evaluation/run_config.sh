#!/usr/bin/env bash
# 한 구성을 처음부터 끝까지 측정한다: 평가 DB 복사 → 라우터 → 앱 기동 → 평가셋 180건 → 판정 차단 집계 → 정리
#
# 사용: run_config.sh <구성 이름> <판정기 켜기 true|false> [judge_router.py 인자...]
#   예) run_config.sh C true --chat vllm --judge bedrock --vllm-url http://<EC2 IP>:8000
#   기준선은 환경 변수로 준다: QA_T=0.71 QO_T=0.80 (없으면 현행 0.72 / 0.88)
#
# 필요한 것: 빌드한 앱 jar(APP_JAR, 기본 build/libs/telme-0.0.1-SNAPSHOT.jar), FAQ와 임베딩이 있는 템플릿 DB(TEMPLATE_DB, 기본 telme),
#           PostgreSQL 컨테이너(PG_CONTAINER, 기본 telme-flow-postgres, 포트 PG_PORT 기본 5440), 로컬 Ollama(bge-m3, exaone3.5:7.8b)
set -uo pipefail
NAME=$1; JUDGE=$2; shift 2
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
OUT="${OUT_DIR:-$ROOT/.measure/judge-serving}/$NAME"
APP_JAR="${APP_JAR:-$ROOT/build/libs/telme-0.0.1-SNAPSHOT.jar}"
PG_CONTAINER="${PG_CONTAINER:-telme-flow-postgres}"
PG_PORT="${PG_PORT:-5440}"
TEMPLATE_DB="${TEMPLATE_DB:-telme}"
APP_PORT="${APP_PORT:-8101}"
ROUTER_PORT="${ROUTER_PORT:-11435}"
DB="telme_judge_eval_$(echo "$NAME" | tr '[:upper:]' '[:lower:]')"
mkdir -p "$OUT"

stop_port() {
  local pid
  pid=$(netstat -ano 2>/dev/null | grep -E ":$1 .*LISTEN" | awk '{print $NF}' | head -1)
  [ -n "$pid" ] && { taskkill //PID "$pid" //F >/dev/null 2>&1 || kill "$pid" 2>/dev/null; }
}
cleanup() {
  stop_port "$APP_PORT"; stop_port "$ROUTER_PORT"
  docker exec "$PG_CONTAINER" psql -U telme -d postgres -qc "DROP DATABASE IF EXISTS $DB WITH (FORCE)"
}
trap cleanup EXIT

docker exec "$PG_CONTAINER" psql -U telme -d postgres -qc "DROP DATABASE IF EXISTS $DB WITH (FORCE)" \
  -c "CREATE DATABASE $DB TEMPLATE $TEMPLATE_DB"

python -X utf8 "$HERE/judge_router.py" --port "$ROUTER_PORT" --log "$OUT/router.jsonl" "$@" > "$OUT/router.log" 2>&1 &
sleep 4
cat "$OUT/router.log"

OLLAMA_URL="http://127.0.0.1:$ROUTER_PORT" LLM_PROVIDER=ollama RAG_EVIDENCE_CHECK_ENABLED="$JUDGE" \
  SEARCH_SIMILARITY_THRESHOLD="${QA_T:-0.72}" SEARCH_DUAL_VECTOR_QUESTION_THRESHOLD="${QO_T:-0.88}" \
  POSTGRES_PORT="$PG_PORT" POSTGRES_DB="$DB" SERVER_PORT="$APP_PORT" \
  java -jar "$APP_JAR" > "$OUT/app.log" 2>&1 &
for _ in $(seq 1 90); do grep -aq "Started TelmeApplication\|APPLICATION FAILED" "$OUT/app.log" 2>/dev/null && break; sleep 2; done
grep -aq "Started TelmeApplication" "$OUT/app.log" || { echo "앱 기동 실패: $OUT/app.log"; exit 1; }

python -X utf8 "$ROOT/scripts/single-faq-search-evaluation/run_chat_eval.py" --base-url "http://localhost:$APP_PORT" \
  --database "$DB" --output "$OUT/chat.json" > "$OUT/chat.log" 2>&1
echo "eval exit=$?"

# 판정기가 막은 실행(guard EVIDENCE_NOT_RELEVANT)을 질문 원문과 함께 남긴다
docker exec "$PG_CONTAINER" psql -U telme -d "$DB" -Atc "
  SELECT json_agg(json_build_object('question', m.content,
    'blocked', EXISTS (SELECT 1 FROM jsonb_array_elements(CASE WHEN jsonb_typeof(e.pipeline_trace->'guard')='array'
                         THEN e.pipeline_trace->'guard' ELSE jsonb_build_array(e.pipeline_trace->'guard') END) g
                       WHERE g->>'reason'='EVIDENCE_NOT_RELEVANT')))
  FROM chat_executions e JOIN chat_messages m ON m.message_id=e.input_message_id" > "$OUT/guard.json"
echo "done $NAME"
