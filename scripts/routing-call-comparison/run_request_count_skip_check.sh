#!/usr/bin/env bash
# TELME-175 2단계 추가 확인: 요청 개수 판정 호출을 끄면(telme.routing.request-inventory-enabled=false) 어떻게 되는지
# 앱 라우팅 전체 경로(routeSingleConsult)로 FAQ 210문항의 최종 하위 질문 개수를 잰다.
#   exaone-full          모든 라우팅 호출 EXAONE, 판정 호출 켬(지금 develop과 같은 구성), 1회
#   route-a-gptoss-N     모든 라우팅 호출 gpt-oss(라우터), 판정 호출 켬, 3회
#   route-b-gptoss-N     모든 라우팅 호출 gpt-oss(라우터), 판정 호출 끔, 3회
#   route-a-app-gptoss-N / route-b-app-gptoss-N   위와 같되 gpt-oss를 앱의 BedrockClient로 직접 부름, 3회씩
#
# 준비: run_intent_step.sh와 같다(Ollama EXAONE, Bedrock 라우터). 앱 클라이언트 실행에는 Bedrock 자격 증명이 필요하다
# 결과: scripts/routing-call-comparison/results/step2-request-count/results.json.gz에 위 키를 더한다(기존 키는 그대로 둔다)
# 채점: python scripts/routing-call-comparison/score_request_count_skip.py
set -euo pipefail
cd "$(dirname "$0")/../.."

EXAONE_URL=${EXAONE_URL:-http://localhost:11434}
GPTOSS_URL=${GPTOSS_URL:-http://127.0.0.1:11435}
OUT=scripts/routing-call-comparison/results/step2-request-count
TMP=build/tmp/routing-call-comparison/skip-check
mkdir -p "$OUT" "$TMP"

python -X utf8 -c "import json; json.dump([i for i in json.load(open('scripts/data/eval_routing_unified.json', encoding='utf-8'))['items'] if i['expected_intent'] == 'FAQ'], open('$TMP/faq-items.json', 'w', encoding='utf-8'), ensure_ascii=False)"

run() {  # $1=결과 이름 $2=라우팅 호출 주소 $3=판정 호출 끔(true/false) $4=회차 수. 나머지 환경 변수는 호출하는 쪽에서 붙인다
  TELME_ROUTING_EVAL=true TELME_ROUTING_INTENT_URL="$2" TELME_ROUTING_INVENTORY_URL="$2" \
    TELME_ROUTING_FAITHFULNESS_URL="$2" TELME_ROUTING_SKIP_INVENTORY="$3" \
    TELME_ROUTING_REPEATS="$4" TELME_ROUTING_RUN_PREFIX="$1" \
    TELME_ROUTING_INPUT="$TMP/faq-items.json" TELME_ROUTING_OUT="$TMP/$1.json" \
    ./gradlew test --tests 'com.telme.probe.RoutingEvaluationProbe' --rerun -q
  echo "$1 끝 $(date +%H:%M:%S)"
}

run exaone-full "$EXAONE_URL" false 1
TELME_ROUTING_BASE_URL="$GPTOSS_URL" run route-a-gptoss "$GPTOSS_URL" false 3
TELME_ROUTING_BASE_URL="$GPTOSS_URL" run route-b-gptoss "$GPTOSS_URL" true 3
# 앱 클라이언트 모드는 BASE_URL과 다른 주소로 가는 호출을 BedrockClient로 보낸다
TELME_ROUTING_GPTOSS_CLIENT=bedrock TELME_ROUTING_BASE_URL="$EXAONE_URL" run route-a-app-gptoss "$GPTOSS_URL" false 3
TELME_ROUTING_GPTOSS_CLIENT=bedrock TELME_ROUTING_BASE_URL="$EXAONE_URL" run route-b-app-gptoss "$GPTOSS_URL" true 3

python -X utf8 -c "
import gzip, json, sys
tmp, out = sys.argv[1], sys.argv[2]
path = f'{out}/results.json.gz'
runs = json.loads(gzip.decompress(open(path, 'rb').read()))
runs['exaone-full'] = json.load(open(f'{tmp}/exaone-full.json', encoding='utf-8'))
for name in ('route-a-gptoss', 'route-b-gptoss', 'route-a-app-gptoss', 'route-b-app-gptoss'):
    runs.update(json.load(open(f'{tmp}/{name}.json', encoding='utf-8')))
open(path, 'wb').write(gzip.compress(json.dumps(runs, ensure_ascii=False, indent=1).encode('utf-8'), mtime=0))
" "$TMP" "$OUT"
