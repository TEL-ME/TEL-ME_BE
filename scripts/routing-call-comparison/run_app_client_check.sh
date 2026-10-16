#!/usr/bin/env bash
# TELME-175 실제 클라이언트 재확인(PR #160 리뷰): 1~4단계의 gpt-oss 3회를 비교용 라우터(judge_router.py) 대신
# 앱의 BedrockClient·BedrockRequestConverter로 직접 부른다. 라우터와 다른 점은 둘이다.
#   - 라우터는 JSON 출력 지시를 사용자 메시지 끝에 덧붙인다. 앱은 덧붙이지 않고 프롬프트에 맡긴다
#   - 라우터는 gpt-oss 출력 한도를 최소 512로 올린다. 앱은 요청 값을 그대로 쓴다(요청 개수 판정·검색어 검증 256, 후속 분석 300)
# 추론 강도는 둘 다 low다. EXAONE 쪽 호출(1단계의 요청 개수 판정·검색어 검증)은 각 단계 스크립트와 같이 EXAONE으로 둔다.
#
# 준비
#   - Ollama: exaone3.5:7.8b (기본 http://localhost:11434)
#   - Bedrock 자격 증명(AWS_PROFILE 등)과 지역(LLM_BEDROCK_REGION, 기본 ap-northeast-1)
# 결과: scripts/routing-call-comparison/results/app-client-check/results.json.gz
#       ({"step1-gptoss-1": 1단계 프로브 출력, ..., "step4-gptoss-3": ..., "client-errors": {단계: 호출별 오류 수}})
# 채점: 각 단계 채점 스크립트에 --results 이 파일과 이름을 넘긴다. 예:
#   python scripts/routing-call-comparison/score_request_count_step.py step2-gptoss-1 step2-gptoss-2 step2-gptoss-3 \
#     --results scripts/routing-call-comparison/results/app-client-check/results.json.gz
set -euo pipefail
cd "$(dirname "$0")/../.."

EXAONE_URL=${EXAONE_URL:-http://localhost:11434}
OUT=scripts/routing-call-comparison/results/app-client-check
TMP=build/tmp/routing-call-comparison/app-client
mkdir -p "$OUT" "$TMP"

python -X utf8 -c "
import json
items = json.load(open('scripts/data/eval_routing_unified.json', encoding='utf-8'))['items']
json.dump(items, open('$TMP/items.json', 'w', encoding='utf-8'), ensure_ascii=False)
json.dump([i for i in items if i['expected_intent'] == 'FAQ'], open('$TMP/faq-items.json', 'w', encoding='utf-8'), ensure_ascii=False)
json.dump(json.load(open('scripts/data/eval_routing_faithfulness.json', encoding='utf-8'))['cases'], open('$TMP/faith-cases.json', 'w', encoding='utf-8'), ensure_ascii=False)
json.dump(json.load(open('scripts/data/eval_routing_followup.json', encoding='utf-8'))['cases'], open('$TMP/followup-cases.json', 'w', encoding='utf-8'), ensure_ascii=False)
"

run() {  # $1=단계 이름 $2=프로브 모드 $3=입력 파일. 나머지 환경 변수는 호출하는 쪽에서 붙인다
  TELME_ROUTING_EVAL=true TELME_ROUTING_GPTOSS_CLIENT=bedrock TELME_ROUTING_BASE_URL="$EXAONE_URL" \
    TELME_ROUTING_STEP="$2" TELME_ROUTING_REPEATS=3 TELME_ROUTING_RUN_PREFIX="$1-gptoss" \
    TELME_ROUTING_INPUT="$3" TELME_ROUTING_OUT="$TMP/$1.json" \
    ./gradlew test --tests 'com.telme.probe.RoutingEvaluationProbe' --rerun -q
  echo "$1 끝 $(date +%H:%M:%S)"
}

# 1단계는 의도 분류만 gpt-oss다(요청 개수 판정·검색어 검증 주소를 EXAONE으로 두면 BASE_URL과 같아 EXAONE으로 간다)
TELME_ROUTING_INVENTORY_URL="$EXAONE_URL" TELME_ROUTING_FAITHFULNESS_URL="$EXAONE_URL" \
  run step1 intent "$TMP/items.json"
run step2 inventory "$TMP/faq-items.json"
run step3 faithfulness "$TMP/faith-cases.json"
run step4 followup "$TMP/followup-cases.json"

python -X utf8 -c "
import gzip, json, sys
tmp, out = sys.argv[1], sys.argv[2]
runs, errors = {}, {}
for step in ('step1', 'step2', 'step3', 'step4'):
    runs.update(json.load(open(f'{tmp}/{step}.json', encoding='utf-8')))
    errors[step] = json.load(open(f'{tmp}/{step}.json.client-errors.json', encoding='utf-8'))
runs['client-errors'] = errors
open(f'{out}/results.json.gz', 'wb').write(gzip.compress(json.dumps(runs, ensure_ascii=False, indent=1).encode('utf-8'), mtime=0))
" "$TMP" "$OUT"
