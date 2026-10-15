#!/usr/bin/env bash
# TELME-175 2단계: 요청 개수 판정 호출(REQUEST_INVENTORY_PROMPT)만 EXAONE / gpt-oss로 바꿔 비교한다.
# 앱에서는 의도 분류(1단계에서 gpt-oss로 확정)가 FAQ 하위 질문을 2개 이상 만들었을 때만 이 호출이 돈다.
# 입력이 질문 하나뿐이라, 통합 평가셋의 FAQ 문항 전체에 이 호출만 직접 보내 나누기(여러 요청)와
# 합치기(한 요청) 양쪽을 시험한다. gpt-oss는 결과가 흔들려 3회를 동시에 돈다.
#
# 준비: run_intent_step.sh와 같다(Ollama EXAONE, Bedrock 라우터)
# 결과: scripts/routing-call-comparison/results/step2-request-count/results.json.gz
#       ({"exaone": 프로브 출력, "gptoss-1": ..., "gptoss-2": ..., "gptoss-3": ...})
# 채점: python scripts/routing-call-comparison/score_request_count_step.py
set -euo pipefail
cd "$(dirname "$0")/../.."

EXAONE_URL=${EXAONE_URL:-http://localhost:11434}
GPTOSS_URL=${GPTOSS_URL:-http://127.0.0.1:11435}
OUT=scripts/routing-call-comparison/results/step2-request-count
TMP=build/tmp/routing-call-comparison
mkdir -p "$OUT" "$TMP"

# 요청 개수 판정은 FAQ 의도에서만 돈다
python -X utf8 -c "import json; json.dump([i for i in json.load(open('scripts/data/eval_routing_unified.json', encoding='utf-8'))['items'] if i['expected_intent'] == 'FAQ'], open('$TMP/faq-items.json', 'w', encoding='utf-8'), ensure_ascii=False)"

run() {  # $1=결과 파일 이름 $2=요청 개수 판정 호출 주소 $3=회차 수
  TELME_ROUTING_EVAL=true TELME_ROUTING_STEP=inventory TELME_ROUTING_INVENTORY_URL="$2" \
    TELME_ROUTING_REPEATS="$3" TELME_ROUTING_RUN_PREFIX="$1" \
    TELME_ROUTING_INPUT="$TMP/faq-items.json" TELME_ROUTING_OUT="$TMP/step2-$1.json" \
    ./gradlew test --tests 'com.telme.probe.RoutingEvaluationProbe' --rerun -q
  echo "$1 끝 $(date +%H:%M:%S)"
}

run exaone "$EXAONE_URL" 1
run gptoss "$GPTOSS_URL" 3

python -X utf8 -c "
import gzip, json, sys
tmp, out = sys.argv[1], sys.argv[2]
runs = {'exaone': json.load(open(f'{tmp}/step2-exaone.json', encoding='utf-8'))}
runs.update(json.load(open(f'{tmp}/step2-gptoss.json', encoding='utf-8')))
open(f'{out}/results.json.gz', 'wb').write(gzip.compress(json.dumps(runs, ensure_ascii=False, indent=1).encode('utf-8'), mtime=0))
" "$TMP" "$OUT"
