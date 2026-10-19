"""TELME-175 2단계 채점: 요청 개수 판정 호출 결과를 acceptable_request_counts로 채점한다.

앱은 판정이 SINGLE·COMPARISON이면 하위 질문을 하나로 합치고, MULTIPLE이면 나눈 채로 두고,
판정이 실패하면 질문 분해를 보류(복합 질문 안내)한다. 그래서 오류를 아래처럼 나눠 센다.
  - 잘못 나눔: 한 요청인데 2개 이상으로 판정
  - 잘못 합침: 여러 요청인데 1개로 판정(요청 일부가 답에서 빠진다)
  - 개수 틀림: 여러 요청이고 2개 이상으로 판정했지만 개수가 다름
  - 판정 실패: 응답 형식 오류 등(복합 질문 안내로 끝난다)
앱 흐름에서 실제로 이 호출이 도는 문항(1단계 gpt-oss 의도 분류가 FAQ 하위 질문을 2개 이상 만든 문항)만 따로도 본다.

사용: python scripts/routing-call-comparison/score_request_count_step.py [이름...]
"""
import argparse
import collections
import gzip
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESULTS = ROOT / "scripts/routing-call-comparison/results"
parser = argparse.ArgumentParser()
parser.add_argument("names", nargs="*", default=["exaone", "gptoss-1", "gptoss-2", "gptoss-3"])
parser.add_argument("--results", default=str(RESULTS / "step2-request-count/results.json.gz"))
parser.add_argument("--step1", default=str(RESULTS / "step1-intent/results.json.gz"))
parser.add_argument("--eval-set", default=str(ROOT / "scripts/data/eval_routing_unified.json"))
args = parser.parse_args()


def load_gz(path):
    return json.loads(gzip.decompress(Path(path).read_bytes()).decode("utf-8"))


runs = load_gz(args.results)
items = {i["eval_id"]: i for i in json.loads(Path(args.eval_set).read_text(encoding="utf-8"))["items"]}

# 앱 흐름 대상: 1단계 gpt-oss 회차 중 하나라도 FAQ 하위 질문을 2개 이상 만든 문항
flow = set()
for name, result in load_gz(args.step1).items():
    if not name.startswith("gptoss"):
        continue
    for r in result["records"]:
        try:
            response = json.loads(r.get("model_response") or "{}")
        except json.JSONDecodeError:
            continue
        if response.get("intent") == "FAQ" and len(response.get("subQueries") or []) > 1:
            flow.add(r["eval_id"])


def error_type(item, r):
    if r["decision"] == "FAILED":
        return "판정 실패"
    count, acceptable = r["request_count"], item["acceptable_request_counts"]
    if max(acceptable) == 1:
        return "잘못 나눔(1→%d)" % count
    if count == 1:
        return "잘못 합침(%s→1)" % "/".join(map(str, acceptable))
    return "개수 틀림(%s→%d)" % ("/".join(map(str, acceptable)), count)


print(f"{'':<10}{'전체':>9}{'한 요청':>11}{'여러 요청':>11}{'앱 흐름 대상':>13}")
errors = collections.defaultdict(dict)
wrong = collections.defaultdict(list)
for name in args.names:
    records = runs[name]["records"]
    # 실행 당시 기록된 일치 여부가 아니라 현재 평가셋 정답으로 다시 채점한다(라벨이 바뀌어도 같은 기준)
    for r in records:
        r["count_matches"] = r["request_count"] in items[r["eval_id"]]["acceptable_request_counts"]
    ok = {r["eval_id"]: r["count_matches"] for r in records}
    single = [r for r in records if max(items[r["eval_id"]]["acceptable_request_counts"]) == 1]
    multiple = [r for r in records if r not in single]
    in_flow = [r for r in records if r["eval_id"] in flow]

    def rate(rows):
        return f"{sum(ok[r['eval_id']] for r in rows):>4}/{len(rows):<4}"

    print(f"{name:<10}{rate(records):>9}  {rate(single):>9}  {rate(multiple):>9}  {rate(in_flow):>11}")
    for t, n in collections.Counter(error_type(items[r["eval_id"]], r) for r in records if not r["count_matches"]).items():
        errors[t][name] = n
    for r in records:
        if not r["count_matches"]:
            wrong[r["eval_id"]].append(f"{name}={r['decision']}:{r['request_count']}")

print("\n오류 유형(실행별 건수)")
for t in sorted(errors, key=lambda t: -sum(errors[t].values())):
    print(f"  {t:<22}" + "  ".join(f"{n}:{errors[t].get(n, 0)}" for n in args.names))

print("\n틀린 문항")
for eval_id, got in sorted(wrong.items()):
    i = items[eval_id]
    mark = " [앱 흐름]" if eval_id in flow else ""
    print(f"  {eval_id} 정답 {'/'.join(map(str, i['acceptable_request_counts'])):<4} {', '.join(got):<60} {i['question'][:45]}{mark}")
