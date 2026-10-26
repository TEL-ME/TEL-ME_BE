"""사람 판정(human-verdicts.json)을 반영해 통과율을 다시 내고, Judge 결합 판정이 사람과 어디서 어긋났는지 센다. 모델을 부르지 않는다.

- 답 하나의 최종 판정: 사람이 본 답은 사람 판정, 세 Judge가 같은 답은 그 판정, 나머지(Judge가 갈렸는데 사람이 안 본 답)는
  any-fail 결합 또는 다수결 결합으로 채운다. 두 방식을 모두 낸다
- 통과율은 입력마다 엑사원 1회차와 gpt-oss 각 회차를 비교한다(엑사원은 세 회차 답이 같다)
- A는 표본만 사람이 봐서 통과율은 다시 내지 않고, Judge 결합과 사람 판정의 어긋남만 센다

사용(저장소 루트에서): python -X utf8 scripts/chat_judge/answer_generation/apply_human_verdicts.py <capture-*.json.gz와 runs/가 있는 폴더> <human-verdicts.json> --out <요약.json>
"""
import argparse
import collections
import gzip
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
from scripts.chat_judge import combine_judges as cj  # noqa: E402

JUDGES = ("qwen", "deepseek-v3.2-usw2", "mistral-large-3")
RUNS = (("exaone", 1), ("gptoss", 1), ("gptoss", 2), ("gptoss", 3))


def load(results, s):
    cap = {c["caseId"]: c for c in json.load(gzip.open(results / f"capture-{s}.json.gz", "rt", encoding="utf-8"))["cases"]}
    runs = {m: {t["caseId"]: t for t in json.load(gzip.open(results / f"runs/{s}-{m}.json.gz", "rt", encoding="utf-8"))["turns"]}
            for m in JUDGES}
    return cap, runs


def turn_states(cap, runs, human):
    states = {}
    for cid, case in cap.items():
        labels = [cj.turn_labels(runs[m][cid]) for m in JUDGES]
        votes = [runs[m][cid]["answerQualityPass"] for m in JUDGES]
        any_fail = cj.combine_any_fail(labels)["pass"]
        key = cid.replace("|", "__")
        verdict = (human.get(key) or {}).get("verdict")
        states[cid] = {"model": case["model"], "anyFail": any_fail, "majority": cj.combine_majority(labels)["pass"],
                       "contested": any_fail is None or None in votes or (True in votes and False in votes),
                       "human": {"pass": True, "fail": False}.get(verdict)}
    return states


def final(state, fallback):
    if state["human"] is not None:
        return state["human"]
    if not state["contested"]:
        return state["anyFail"]
    return state[fallback]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("results", type=Path)
    parser.add_argument("verdicts", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    human = json.loads(args.verdicts.read_text(encoding="utf-8"))["verdicts"]
    report = {}
    for s in ("A", "B"):
        cap, runs = load(args.results, s)
        states = turn_states(cap, runs, human)
        # Judge 결합과 사람 판정 비교(사람이 본, 판정이 갈린 답)
        compare = collections.Counter()
        for st in states.values():
            if st["human"] is None or not st["contested"]:
                continue
            for rule in ("anyFail", "majority"):
                judged = st[rule]
                outcome = ("일치" if judged == st["human"] else "Judge 미확정" if judged is None
                           else "Judge 실패, 사람 통과" if st["human"] else "Judge 통과, 사람 실패")
                compare[f"{st['model']}|{rule}|{outcome}"] += 1
        entry = {"reviewedContested": sum(1 for st in states.values() if st["contested"] and st["human"] is not None),
                 "judgeVsHuman": dict(sorted(compare.items()))}
        if s == "B":
            by = {(c["inputId"], c["model"], r): cid for cid, c in cap.items() for r in c["runs"]}
            inputs = sorted({key[0] for key in by})
            rates = {}
            for fallback in ("anyFail", "majority"):
                for model, run in RUNS:
                    values = [final(states[by[(i, model, run)]], fallback) for i in inputs]
                    decided = [v for v in values if v is not None]
                    rates[f"{fallback}|{model}-r{run}"] = {
                        "pass": sum(decided), "decided": len(decided), "undecided": len(values) - len(decided),
                        "pct": round(100 * sum(decided) / len(decided), 1),
                        "contestedNotReviewed": sum(1 for i in inputs if states[by[(i, model, run)]]["contested"]
                                                    and states[by[(i, model, run)]]["human"] is None)}
            entry["rates"] = rates
        report[s] = entry
    args.out.write_text(json.dumps(report, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    for key, value in report["B"]["rates"].items():
        print(f"B {key}: {value['pass']}/{value['decided']} = {value['pct']}% | 미확정 {value['undecided']} | 사람이 안 본 갈림 {value['contestedNotReviewed']}")
    for s in ("A", "B"):
        print(s, "사람이 본 갈린 답", report[s]["reviewedContested"], report[s]["judgeVsHuman"])


if __name__ == "__main__":
    main()
