#!/usr/bin/env bash
# TELME-175 3단계: 검색어 검증 호출(FAQ_QUERY_FAITHFULNESS_PROMPT)만 EXAONE / gpt-oss로 바꿔 비교한다.
# 입력은 scripts/data/eval_routing_faithfulness.json이다(1단계 gpt-oss 1회차 의도 분류 출력에서 앱 코드로 만든
# 실제 입력 + 뜻을 일부러 바꾼 검색어, 정답 포함). 앱이 모델을 부르지 않는 경우(skipped)는 건너뛴다.
# 평가셋을 다시 만들려면:
#   TELME_ROUTING_EVAL=true TELME_ROUTING_STEP=faithfulness-cases \
#     TELME_ROUTING_INPUT=<1단계 results.json.gz의 gptoss-1 records를 풀어 쓴 파일> \
#     TELME_ROUTING_OUT=build/tmp/routing-call-comparison/faith-cases.json \
#     ./gradlew test --tests 'com.telme.probe.RoutingEvaluationProbe' --rerun
#   python scripts/routing-call-comparison/build_faithfulness_set.py
#
# 준비: run_intent_step.sh와 같다(Ollama EXAONE, Bedrock 라우터)
# 결과: scripts/routing-call-comparison/results/step3-faithfulness/results.json.gz
#       ({"exaone": 프로브 출력, "gptoss-1": ..., "gptoss-2": ..., "gptoss-3": ...})
# 채점: python scripts/routing-call-comparison/score_faithfulness_step.py
set -euo pipefail
cd "$(dirname "$0")/../.."

EXAONE_URL=${EXAONE_URL:-http://localhost:11434}
GPTOSS_URL=${GPTOSS_URL:-http://127.0.0.1:11435}
OUT=scripts/routing-call-comparison/results/step3-faithfulness
TMP=build/tmp/routing-call-comparison
mkdir -p "$OUT" "$TMP"

python -X utf8 -c "import json; json.dump(json.load(open('scripts/data/eval_routing_faithfulness.json', encoding='utf-8'))['cases'], open('$TMP/faith-cases-labeled.json', 'w', encoding='utf-8'), ensure_ascii=False)"

run() {  # $1=결과 파일 이름 $2=검색어 검증 호출 주소 $3=회차 수
  TELME_ROUTING_EVAL=true TELME_ROUTING_STEP=faithfulness TELME_ROUTING_FAITHFULNESS_URL="$2" \
    TELME_ROUTING_REPEATS="$3" TELME_ROUTING_RUN_PREFIX="$1" \
    TELME_ROUTING_INPUT="$TMP/faith-cases-labeled.json" TELME_ROUTING_OUT="$TMP/step3-$1.json" \
    ./gradlew test --tests 'com.telme.probe.RoutingEvaluationProbe' --rerun -q
  echo "$1 끝 $(date +%H:%M:%S)"
}

run exaone "$EXAONE_URL" 1
run gptoss "$GPTOSS_URL" 3

python -X utf8 -c "
import gzip, json, sys
tmp, out = sys.argv[1], sys.argv[2]
runs = {'exaone': json.load(open(f'{tmp}/step3-exaone.json', encoding='utf-8'))}
runs.update(json.load(open(f'{tmp}/step3-gptoss.json', encoding='utf-8')))
open(f'{out}/results.json.gz', 'wb').write(gzip.compress(json.dumps(runs, ensure_ascii=False, indent=1).encode('utf-8'), mtime=0))
" "$TMP" "$OUT"
