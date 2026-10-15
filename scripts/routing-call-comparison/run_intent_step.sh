#!/usr/bin/env bash
# TELME-175 1단계: 라우팅 호출 중 의도 분류만 EXAONE / gpt-oss로 바꿔 통합 평가셋(314문항)으로 비교한다.
# 요청 개수 판정·검색어 검증 등 나머지 라우팅 호출은 EXAONE으로 고정한다. gpt-oss는 결과가 흔들려 3회 돈다.
#
# 준비
#   - Ollama: exaone3.5:7.8b (기본 http://localhost:11434)
#   - gpt-oss: Bedrock 라우터(기본 http://127.0.0.1:11435)
#       AWS_PROFILE=<프로필> LLM_BEDROCK_REGION=ap-northeast-1 \
#         python scripts/judge-serving-evaluation/judge_router.py --chat bedrock --judge bedrock
# 결과: scripts/routing-call-comparison/results/step1-intent/results.json.gz
#       ({"exaone": 프로브 출력, "gptoss-1": ..., "gptoss-2": ..., "gptoss-3": ...})
# 채점: python scripts/routing-call-comparison/score_intent_step.py
set -euo pipefail
cd "$(dirname "$0")/../.."

EXAONE_URL=${EXAONE_URL:-http://localhost:11434}
GPTOSS_URL=${GPTOSS_URL:-http://127.0.0.1:11435}
OUT=scripts/routing-call-comparison/results/step1-intent
TMP=build/tmp/routing-call-comparison
mkdir -p "$OUT" "$TMP"

# 프로브는 문항 배열을 읽는다
python -X utf8 -c "import json; json.dump(json.load(open('scripts/data/eval_routing_unified.json', encoding='utf-8'))['items'], open('$TMP/items.json', 'w', encoding='utf-8'), ensure_ascii=False)"

run() {  # $1=결과 이름 $2=의도 분류 호출 주소
  TELME_ROUTING_EVAL=true TELME_ROUTING_BASE_URL="$EXAONE_URL" TELME_ROUTING_INTENT_URL="$2" \
    TELME_ROUTING_INPUT="$TMP/items.json" TELME_ROUTING_OUT="$TMP/$1.json" \
    ./gradlew test --tests 'com.telme.probe.RoutingEvaluationProbe' --rerun -q
  echo "$1 끝 $(date +%H:%M:%S)"
}

NAMES="exaone gptoss-1 gptoss-2 gptoss-3"
run exaone "$EXAONE_URL"
for n in 1 2 3; do run "gptoss-$n" "$GPTOSS_URL"; done

# 실행별 프로브 출력을 단계당 압축 파일 하나로 묶는다
python -X utf8 -c "
import gzip, json, sys
tmp, out, names = sys.argv[1], sys.argv[2], sys.argv[3:]
runs = {n: json.load(open(f'{tmp}/{n}.json', encoding='utf-8')) for n in names}
open(f'{out}/results.json.gz', 'wb').write(gzip.compress(json.dumps(runs, ensure_ascii=False, indent=1).encode('utf-8'), mtime=0))
" "$TMP" "$OUT" $NAMES
