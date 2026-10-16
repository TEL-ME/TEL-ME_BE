"""TELME-175 2단계 추가 확인 채점: 앱 라우팅 전체 경로의 최종 하위 질문 개수를 acceptable_request_counts로 채점한다.

run_request_count_skip_check.sh 결과(판정 호출 켬/끔, EXAONE 전체)를 본다. 오류는 아래처럼 나눠 센다.
  - 합침: 여러 요청인데 하위 질문 1개로 끝남(요청 일부가 답에서 빠질 수 있다)
  - 안내: 질문 분해를 보류하고 복합 질문 안내로 끝남(고객이 다시 물어야 한다)
  - 잘못 나눔: 한 요청인데 2개 이상으로 나눔
  - FAQ 아님: 의도 분류가 FAQ가 아닌 의도로 냄(요청 개수와 무관한 1단계 오류)
응답 시간은 모델이 하위 질문을 2개 이상 낸 문항의 라우팅 전체 시간(중앙값)이다.

사용: python scripts/routing-call-comparison/score_request_count_skip.py [이름...]
"""
import argparse
import collections
import gzip
import json
import statistics
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NAMES = (["exaone-full"] + [f"route-{c}-gptoss-{n}" for c in ("a", "b") for n in (1, 2, 3)]
         + [f"route-{c}-app-gptoss-{n}" for c in ("a", "b") for n in (1, 2, 3)])
parser = argparse.ArgumentParser()
parser.add_argument("names", nargs="*", default=NAMES)
parser.add_argument("--results", default=str(ROOT / "scripts/routing-call-comparison/results/step2-request-count/results.json.gz"))
parser.add_argument("--eval-set", default=str(ROOT / "scripts/data/eval_routing_unified.json"))
args = parser.parse_args()
runs = json.loads(gzip.decompress(Path(args.results).read_bytes()).decode("utf-8"))
items = {i["eval_id"]: i for i in json.loads(Path(args.eval_set).read_text(encoding="utf-8"))["items"]}


def kind(item, record):
    acceptable = item["acceptable_request_counts"]
    if record.get("compound_guidance"):
        return "안내"
    if record["intent"] != "FAQ":
        return "FAQ 아님"
    count = record.get("sub_query_count")
    if count in acceptable:
        return "맞음"
    if max(acceptable) == 1:
        return "잘못 나눔"
    return "합침" if count == 1 else "개수 틀림"


def model_split(record):
    try:
        return len(json.loads(record.get("model_response") or "{}").get("subQueries") or []) > 1
    except json.JSONDecodeError:
        return False


print(f"{'':<22}{'여러 요청 맞음':>10}{'합침':>6}{'안내':>6}{'한 요청 맞음':>10}{'잘못 나눔':>8}{'안내':>6}{'FAQ 아님':>8}{'응답(ms)':>9}")
wrong = collections.defaultdict(list)
for name in args.names:
    groups = collections.defaultdict(collections.Counter)
    elapsed = []
    for record in runs[name]["records"]:
        item = items[record["eval_id"]]
        group = "multi" if max(item["acceptable_request_counts"]) > 1 else "single"
        result = kind(item, record)
        groups[group][result] += 1
        if model_split(record):
            elapsed.append(record["elapsed_ms"])
        if result not in ("맞음", "FAQ 아님"):
            wrong[record["eval_id"]].append(f"{name}:{result}")
    multi, single = groups["multi"], groups["single"]
    print(f"{name:<22}{multi['맞음']:>6}/{sum(multi.values()):<4}{multi['합침']:>6}{multi['안내']:>6}"
          f"{single['맞음']:>6}/{sum(single.values()):<4}{single['잘못 나눔']:>8}{single['안내']:>6}"
          f"{single['FAQ 아님'] + multi['FAQ 아님']:>8}{statistics.median(elapsed) if elapsed else 0:>9.0f}")

print("\n틀린 문항(FAQ 아님 제외)")
for eval_id in sorted(wrong):
    print(f"  {eval_id} 정답 {'/'.join(map(str, items[eval_id]['acceptable_request_counts']))}  "
          f"{', '.join(wrong[eval_id])}  {items[eval_id]['question'][:40]}")
