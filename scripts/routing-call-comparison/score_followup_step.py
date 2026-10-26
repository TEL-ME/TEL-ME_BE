"""TELME-175 4단계 채점: 후속 분석 호출 결과를 eval_routing_followup.json 정답으로 채점한다.

케이스가 맞으려면 답변 종류가 맞고, 정답에 적힌 조건이 모두 맞아야 한다.
  FILLED: 그 조건을 FILLED로 채움(지역·업무는 값까지 확인)
  DECLINED: 그 조건을 DECLINED로 표시
  ABSENT: 그 조건을 채우지도 거절로 표시하지도 않음
응답을 읽지 못한 경우(failed)는 앱에서 규칙 분석으로 넘어가므로 틀린 것으로 센다.

사용: python scripts/routing-call-comparison/score_followup_step.py [이름...]
"""
import argparse
import collections
import gzip
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument("names", nargs="*", default=["exaone", "gptoss-1", "gptoss-2", "gptoss-3"])
parser.add_argument("--results", default=str(ROOT / "scripts/routing-call-comparison/results/step4-followup/results.json.gz"))
parser.add_argument("--eval-set", default=str(ROOT / "scripts/data/eval_routing_followup.json"))
args = parser.parse_args()
runs = json.loads(gzip.decompress(Path(args.results).read_bytes()).decode("utf-8"))
cases = {c["case_id"]: c for c in json.loads(Path(args.eval_set).read_text(encoding="utf-8"))["cases"]}


def problems(case, record):
    found = []
    if record["failed"]:
        return ["응답 형식 오류"]
    if record["response_type"] not in case["response_types"]:
        found.append(f"답변 종류 {record['response_type']}(정답 {'/'.join(case['response_types'])})")
    got = {c["key"]: c for c in record["conditions"] or []}
    for key, gold in case["conditions"].items():
        actual = got.get(key)
        if gold["status"] == "ABSENT":
            if actual is not None:
                found.append(f"{key}를 {actual['status']}로 채움({actual.get('value')})")
            continue
        if actual is None or actual["status"] != gold["status"]:
            found.append(f"{key} {gold['status']} 아님({actual['status'] if actual else '없음'})")
            continue
        if gold.get("value"):
            value = (actual.get("value") or "").strip()
            ok = value == gold["value"] if gold["match"] == "exact" else gold["value"] in value
            if not ok:
                found.append(f"{key} 값 {value!r}(정답 {gold['value']})")
    return found


print(f"{'':<12}{'전체':>8}{'실제 입력':>10}{'새로 쓴 것':>11}{'확인용':>8}{'조건 답변':>10}{'보류':>7}{'새 질문':>8}{'형식 오류':>9}")
wrong = collections.defaultdict(list)
for name in args.names:
    groups = collections.defaultdict(list)
    failed = 0
    for record in runs[name]["records"]:
        case = cases[record["case_id"]]
        issues = problems(case, record)
        ok = not issues
        failed += record["failed"]
        for group in ("all", case["source"], case["response_types"][0]):
            groups[group].append(ok)
        if issues:
            wrong[record["case_id"]].append(f"{name}: {'; '.join(issues)}")

    def rate(group):
        values = groups[group]
        return f"{sum(values)}/{len(values)}"

    print(f"{name:<12}{rate('all'):>8}{rate('flow-db'):>10}{rate('written'):>11}{rate('prompt-check'):>8}"
          f"{rate('CONDITION_RESPONSE'):>10}{rate('DEFERRED'):>7}{rate('NEW_QUESTION'):>8}{failed:>9}")

print("\n틀린 케이스")
for case_id in sorted(wrong):
    case = cases[case_id]
    print(f"  {case_id} [{','.join(case['pending_keys'])}] {case['reply']}")
    for line in wrong[case_id]:
        print(f"      {line}")
