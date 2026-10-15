"""TELME-175 3단계 채점: 검색어 검증 호출의 판정을 gold_unsafe로 채점한다.

오류는 두 갈래로 나눠 센다.
  - 통과시킴: 뜻이 바뀐 검색어를 safe로 판정. 앱은 그 검색어로 검색해 다른 FAQ로 답할 수 있다(위험)
  - 막음: 멀쩡한 검색어를 unsafe로 판정. 앱은 원문 인용구나 원문으로 검색한다(덜 다듬은 검색어를 쓰는 손해)
판정 실패(응답 형식 오류)는 앱에서 모두 unsafe로 처리되므로 따로 센다. gold_unsafe=null은 어느 판정이든 맞음.

사용: python scripts/routing-call-comparison/score_faithfulness_step.py [이름...]
"""
import argparse
import collections
import gzip
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument("names", nargs="*", default=["exaone", "gptoss-1", "gptoss-2", "gptoss-3"])
parser.add_argument("--results", default=str(ROOT / "scripts/routing-call-comparison/results/step3-faithfulness/results.json.gz"))
args = parser.parse_args()
runs = json.loads(gzip.decompress(Path(args.results).read_bytes()).decode("utf-8"))


def rate(pairs):
    return f"{sum(ok for ok in pairs):>3}/{len(pairs):<3}"


print(f"{'':<12}{'전체':>8} {'뜻 바뀐 것 잡음':>14} {'멀쩡한 것 통과':>14} {'실제 입력':>10} {'확인용':>8} {'일부러 바꾼 것':>12} {'판정 실패':>8}")
wrong = collections.defaultdict(list)
for name in args.names:
    every, unsafe, safe, real, check, synthetic = [], [], [], [], [], []
    failed = 0
    for r in runs[name]["records"]:
        failed += r["failed"]
        for query, gold, verdict, ok in zip(r["sub_queries"], r["gold_unsafe"], r["unsafe"], r["matches"]):
            every.append(ok)
            # RU: 1단계 gpt-oss 출력에서 만든 실제 입력, CHK: 프롬프트 수정 전에 쓴 확인용, SYN: 일부러 뜻을 바꾼 것
            (synthetic if r["case_id"].startswith("SYN") else check if r["case_id"].startswith("CHK") else real).append(ok)
            if gold is True:
                unsafe.append(ok)
            elif gold is False:
                safe.append(ok)
            if not ok:
                kind = "통과시킴" if gold else "막음"
                wrong[(r["case_id"], query["query"])].append(f"{name}:{kind}")
    print(f"{name:<12}{rate(every):>8} {rate(unsafe):>14} {rate(safe):>14} {rate(real):>10} {rate(check):>8} {rate(synthetic):>12} {failed:>8}")

print("\n틀린 검색어 (통과시킴 = 뜻 바뀐 걸 safe, 막음 = 멀쩡한 걸 unsafe)")
for (case_id, query), got in sorted(wrong.items(), key=lambda x: (-len(x[1]), x[0])):
    print(f"  {case_id:<8} {', '.join(got):<60} {query}")
