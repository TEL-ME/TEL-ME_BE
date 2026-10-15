"""TELME-175 1단계 채점: 의도 분류 결과를 복수 정답(acceptable_intents)과 UNKNOWN 재확인 보정으로 채점한다.

앱(#145)은 모델이 UNKNOWN이라 한 질문을 일반 FAQ 검색으로 다시 보고, 걸리면 FAQ로 바꾼다. 프로브는 이 보정을 하지 않으므로
여기서 검색 테스트 API로 재현한다. 검색 앱은 확정한 임계값으로 띄운다:
  FAQ_SEARCH_TEST_API_ENABLED=true SEARCH_GRAY_ZONE_ENABLED=false LLM_PROVIDER=fake SERVER_PORT=8083
  SEARCH_SIMILARITY_THRESHOLD=0.69 SEARCH_DUAL_VECTOR_QUESTION_THRESHOLD=0.80 java -jar build/libs/telme-0.0.1-SNAPSHOT.jar

사용: python scripts/routing-call-comparison/score_intent_step.py [--search-url URL] [이름...]
"""
import argparse
import collections
import gzip
import json
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument("names", nargs="*", default=["exaone", "gptoss-1", "gptoss-2", "gptoss-3"])
parser.add_argument("--results", default=str(ROOT / "scripts/routing-call-comparison/results/step1-intent/results.json.gz"))
parser.add_argument("--eval-set", default=str(ROOT / "scripts/data/eval_routing_unified.json"))
parser.add_argument("--search-url", default="http://localhost:8083")
args = parser.parse_args()
NAMES = args.names
ALL_RUNS = json.loads(gzip.decompress(Path(args.results).read_bytes()).decode("utf-8"))
items = {i["eval_id"]: i for i in json.loads(Path(args.eval_set).read_text(encoding="utf-8"))["items"]}
cache = {}


def found(q):
    # #145: 라우팅이 UNKNOWN이면 원문으로 일반 검색 기준(0.69/0.80)을 다시 돌려 걸리면 FAQ로 바꾼다
    if q not in cache:
        u = f"{args.search_url}/api/v1/faq/search?" + urllib.parse.urlencode({"query": q, "topK": 1})
        cache[q] = bool(json.load(urllib.request.urlopen(u))["result"])
    return cache[q]


def final(r):
    # 앱은 모델이 직접 UNKNOWN이라 한 경우만 다시 보고(낮은 신뢰도 보류는 제외),
    # 복원 질문이 있으면 그 문장(이전 발언 포함)으로 검색한다
    if r.get("model_intent") == "UNKNOWN" and r["intent"] == "UNKNOWN" \
            and found(r.get("resolved_question") or r["question"]):
        return "FAQ"
    return r["intent"]


def error_type(expected, got):
    if expected == "UNKNOWN":
        return "무관 질문에 답하려 함(UNKNOWN→" + got + ")"
    if got == "UNKNOWN":
        return "통신 질문을 거절(" + expected + "→UNKNOWN)"
    if expected == "BOTH":
        return "복합 질문 한쪽 누락(BOTH→" + got + ")"
    if got == "BOTH":
        return "단일 질문을 복합으로(" + expected + "→BOTH)"
    return "FAQ·매장 혼동(" + expected + "→" + got + ")"


runs = {}
for name in NAMES:
    rows = ALL_RUNS[name]["records"]
    runs[name] = {r["eval_id"]: r for r in rows}

print(f"{'':<10}{'보정 전':>8}{'보정 후':>8}  " + "  ".join(f"{k:>7}" for k in ["FAQ", "STORE", "BOTH", "UNKNOWN"])
      + "   holdout  tuning  general")
per_run = {}
for name, rows in runs.items():
    raw = sum(r["intent"] in items[k]["acceptable_intents"] for k, r in rows.items())
    ok = {k: final(r) in items[k]["acceptable_intents"] for k, r in rows.items()}
    per_run[name] = ok
    by = collections.Counter()
    tot = collections.Counter()
    for k, v in ok.items():
        for g in (items[k]["expected_intent"], items[k]["split"]):
            tot[g] += 1
            by[g] += v
    cells = "  ".join(f"{by[g]:>3}/{tot[g]:<3}" for g in ["FAQ", "STORE", "BOTH", "UNKNOWN"])
    splits = "  ".join(f"{by[g]:>3}/{tot[g]:<3}" for g in ["holdout", "tuning", "general"])
    print(f"{name:<10}{raw:>4}/{len(rows):<3}{sum(ok.values()):>5}    {cells}   {splits}")

print("\n오류 유형(보정 후, 실행별 건수)")
types = collections.defaultdict(dict)
for name, rows in runs.items():
    c = collections.Counter(error_type(items[k]["expected_intent"], final(r))
                            for k, r in rows.items() if not per_run[name][k])
    for t, n in c.items():
        types[t][name] = n
for t in sorted(types, key=lambda t: -sum(types[t].values())):
    print(f"  {t:<34}" + "  ".join(f"{n}:{types[t].get(n, 0)}" for n in NAMES))

print("\n모델별로 틀린 문항(보정 후). 숫자는 틀린 실행 수")
for k in items:
    wrong = [n for n in NAMES if not per_run[n][k]]
    if wrong:
        i = items[k]
        got = {n: final(runs[n][k]) for n in wrong}
        note = " [복수정답]" if len(i["acceptable_intents"]) > 1 else ""
        note += " [팀확인]" if "review_note" in i else ""
        print(f"  {k} {i['expected_intent']:<7} {i['split']:<7} {','.join(f'{n}={g}' for n, g in got.items()):<40} {i['question'][:40]}{note}")
