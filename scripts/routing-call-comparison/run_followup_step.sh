#!/usr/bin/env bash
# TELME-175 4단계: 후속 분석 호출(FOLLOW_UP_SYSTEM_PROMPT)만 EXAONE / gpt-oss로 바꿔 비교한다.
# 되물은 조건에 고객이 답했을 때 도는 호출이다. 입력은 되묻는 중인 조건 이름과 고객 답변뿐이다.
# 평가셋: scripts/data/eval_routing_followup.json (build_followup_set.py로 만든다)
#
# 준비: run_intent_step.sh와 같다(Ollama EXAONE, Bedrock 라우터)
# 결과: scripts/routing-call-comparison/results/step4-followup/results.json.gz
#       ({"exaone": 프로브 출력, "gptoss-1": ..., "gptoss-2": ..., "gptoss-3": ...})
# 채점: python scripts/routing-call-comparison/score_followup_step.py
set -euo pipefail
cd "$(dirname "$0")/../.."

EXAONE_URL=${EXAONE_URL:-http://localhost:11434}
GPTOSS_URL=${GPTOSS_URL:-http://127.0.0.1:11435}
OUT=scripts/routing-call-comparison/results/step4-followup
TMP=build/tmp/routing-call-comparison
mkdir -p "$OUT" "$TMP"

python -X utf8 -c "import json; json.dump(json.load(open('scripts/data/eval_routing_followup.json', encoding='utf-8'))['cases'], open('$TMP/followup-cases.json', 'w', encoding='utf-8'), ensure_ascii=False)"

run() {  # $1=결과 파일 이름 $2=후속 분석 호출 주소 $3=회차 수
  TELME_ROUTING_EVAL=true TELME_ROUTING_STEP=followup TELME_ROUTING_FOLLOWUP_URL="$2" \
    TELME_ROUTING_REPEATS="$3" TELME_ROUTING_RUN_PREFIX="$1" \
    TELME_ROUTING_INPUT="$TMP/followup-cases.json" TELME_ROUTING_OUT="$TMP/step4-$1.json" \
    ./gradlew test --tests 'com.telme.probe.RoutingEvaluationProbe' --rerun -q
  echo "$1 끝 $(date +%H:%M:%S)"
}

run exaone "$EXAONE_URL" 1
run gptoss "$GPTOSS_URL" 3

python -X utf8 -c "
import gzip, json, sys
tmp, out = sys.argv[1], sys.argv[2]
runs = {'exaone': json.load(open(f'{tmp}/step4-exaone.json', encoding='utf-8'))}
runs.update(json.load(open(f'{tmp}/step4-gptoss.json', encoding='utf-8')))
open(f'{out}/results.json.gz', 'wb').write(gzip.compress(json.dumps(runs, ensure_ascii=False, indent=1).encode('utf-8'), mtime=0))
" "$TMP" "$OUT"
