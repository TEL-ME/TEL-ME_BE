#!/bin/bash
# 앱 EC2에서 시나리오 하나를 돈다. 앱을 그 설정으로 다시 띄우고 load_chat.py를 돌린 뒤 같은 구간의 호출 집계를 남긴다.
# 사용: GPU_HOST=<GPU 사설 IP> DB_HOST=<DB 주소> ./run.sh <ollama|vllm> <S0|S1|S2> <N> <질문 수> [라벨 덧붙임]
# 같은 폴더에 bedrock.env(Bedrock 자격 증명, 서명 방식)가 있어야 한다. 호출 집계는 DB 컨테이너(DB_CONTAINER)로 낸다
set -u
CONFIG=$1; SCEN=$2; N=$3; Q=$4; EXTRA=${5:-}
: "${GPU_HOST:?GPU 사설 IP}" "${DB_HOST:?DB 주소}"
DB_CONTAINER=${DB_CONTAINER:-telme-db}
cd "$(dirname "$0")"
LABEL="$CONFIG-$SCEN-N$N$EXTRA"
{
cat <<BASE
POSTGRES_HOST=$DB_HOST
POSTGRES_PORT=${DB_PORT:-5432}
POSTGRES_DB=${DB_NAME:-telme}
LLM_BEDROCK_ENABLED=true
ROUTING_BEDROCK=true
ROUTING_REQUEST_INVENTORY_ENABLED=false
CHAT_QUESTION_RESOLUTION_BEDROCK=true
CONSULT_PLAN_QUESTION_BEDROCK=true
CONSULT_FAQ_CLARIFICATION_BEDROCK=true
RAG_ANSWER_BEDROCK=true
BASE
if [ "$CONFIG" = ollama ]; then
  echo "LLM_PROVIDER=ollama"; echo "OLLAMA_URL=http://$GPU_HOST:11434"
else
  echo "LLM_PROVIDER=vllm"; echo "VLLM_URL=http://$GPU_HOST:8000"
  echo "EMBEDDING_PROVIDER=tei"; echo "EMBEDDING_URL=http://$GPU_HOST:8080"
fi
cat bedrock.env
} > app.env
FILES="-f app-compose.yml"
[ "$SCEN" = S1 ] && FILES="$FILES -f fault-refuse.yml"
[ "$SCEN" = S2 ] && FILES="$FILES -f fault-hang.yml"
sudo docker compose -f app-compose.yml -f fault-hang.yml down >/dev/null 2>&1
sudo docker compose $FILES up -d >/dev/null 2>&1
for i in $(seq 1 60); do sudo docker logs telme-app 2>&1 | grep -q "Started TelmeApplication" && break; sleep 2; done
sudo docker logs telme-app 2>&1 | grep -q "Started TelmeApplication" || { echo "앱 기동 실패"; sudo docker logs telme-app 2>&1 | tail -20; exit 1; }
mkdir -p results
SINCE=$(date -Iseconds)
python3 load_chat.py --concurrency "$N" --questions "$Q" --label "$LABEL" --output "results/$LABEL.jsonl" 2>"results/$LABEL.progress"
UNTIL=$(date -d '+1 min' -Iseconds)
sudo docker exec -i "$DB_CONTAINER" psql -U telme -d "${DB_NAME:-telme}" -v since="'$SINCE'" -v until="'$UNTIL'" \
  < calls.sql > "results/$LABEL.calls.txt"
python3 load_chat.py --summarize "results/$LABEL.jsonl"
