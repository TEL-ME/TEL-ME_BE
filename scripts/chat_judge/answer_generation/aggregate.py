"""세 Judge 모델 결과를 합쳐 13번 답변 생성 모델(엑사원, gpt-oss)을 비교한다. 모델을 부르지 않는다.

- 턴(고유 답변)마다 any-fail(기본)과 다수결로 합친다(TELME-180 combine_judges 함수 그대로)
- 고유 답변 판정을 회차(run 1~3)로 펼쳐 회차별 통과율을 낸다. 엑사원은 3회 답이 같아 세 회차가 같다
- 같은 입력에서 두 모델의 통과 여부를 짝지어 센다(gpt-oss는 회차별)
- 사람 검토 목록: 결합 판정 미확정, Judge 모델끼리 통과/실패가 갈린 답, 라벨 검토 대상 입력

사용(저장소 루트에서): python -X utf8 scripts/chat_judge/answer_generation/aggregate.py <A|B> <capture.json.gz> <qwen.json.gz> <deepseek.json.gz> <mistral.json.gz> --out <요약.json>
"""
import argparse
import collections
import gzip
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[3]))
from scripts.chat_judge import combine_judges as cj  # noqa: E402

J = Path(__file__).resolve().parent / "labels"


def pct(n, d):
    return {"n": n, "d": d, "pct": round(100 * n / d, 1) if d else None}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("set")
    parser.add_argument("capture")
    parser.add_argument("results", nargs=3)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    capture = json.load(gzip.open(args.capture, "rt", encoding="utf-8"))
    cases = {case["caseId"]: case for case in capture["cases"]}
    per_model = {}
    for path in args.results:
        result = json.load(gzip.open(path, "rt", encoding="utf-8"))
        per_model[result["alias"]] = {item["caseId"]: item for item in result["turns"]}
    review_ids = {row["id"] for row in json.loads((J / "review.json").read_text(encoding="utf-8"))}

    turns = {}
    for case_id, case in cases.items():
        labels = {alias: cj.turn_labels(items[case_id]) for alias, items in per_model.items()}
        votes = [label["pass"] for label in labels.values()]
        turns[case_id] = {"anyFail": cj.combine_any_fail(list(labels.values())),
                          "majority": cj.combine_majority(list(labels.values())),
                          "byJudge": labels, "split": True in votes and False in votes,
                          "evidence": [
                              [part["evidenceAnswerability"] for part in
                               (((items[case_id].get("abstention") or {}).get("stages") or {}).get("evidence") or {})
                               .get("result", {}).get("parts", [])]
                              for items in per_model.values()]}

    # 회차로 펼치기: (입력, 모델, 회차) -> 턴
    runs = {}
    for case_id, case in cases.items():
        for run in case["runs"]:
            runs[(case["inputId"], case["model"], run)] = case_id

    def behavior(case_id):
        return cases[case_id]["turns"][0]["fixture"]["expectedBehavior"]

    def evidence_enough(case_id):
        """세 Judge 모두 근거가 모든 질문 부분을 답한다고 본 경우만 '근거 충분'으로 친다."""
        judged = [parts for parts in turns[case_id]["evidence"] if parts]
        return bool(judged) and all(all(value == "ENOUGH" for value in parts) for parts in judged)

    def summary(keys, rule):
        verdicts = [turns[runs[key]][rule] for key in keys]
        count = collections.Counter(v["pass"] for v in verdicts)
        decided = count[True] + count[False]
        return {"turns": len(keys), "pass": pct(count[True], decided), "undecided": count[None],
                "unsupported": pct(sum(v["grounding"] == "UNSUPPORTED" for v in verdicts), len(keys)),
                "qualityComplete": pct(sum(v["quality"] in ("COMPLETE", "NOT_APPLICABLE") for v in verdicts), len(keys)),
                "overRefusal": pct(sum(v["abstention"] == "OVER_REFUSAL" for v in verdicts), len(keys)),
                "shouldAbstain": pct(sum(v["abstention"] == "SHOULD_ABSTAIN" for v in verdicts), len(keys))}

    inputs = sorted({key[0] for key in runs})
    first_case = {key[0]: case_id for key, case_id in runs.items()}
    group_of = {item: cases[first_case[item]]["category"] for item in inputs}
    slices = {"전체": inputs,
              "답할 수 있는 질문": [i for i in inputs if behavior(first_case[i]) != "ABSTAIN"],
              "답 없음(ABSTAIN)": [i for i in inputs if behavior(first_case[i]) == "ABSTAIN"],
              "근거 충분(엑사원 턴 기준)": [i for i in inputs if evidence_enough(runs[(i, "exaone", 1)])],
              "라벨 검토 대상 제외": [i for i in inputs if i not in review_ids]}
    for group in sorted(set(group_of.values())):
        slices[f"묶음:{group}"] = [i for i in inputs if group_of[i] == group]

    report = {"set": args.set, "judges": list(per_model), "slices": {}}
    for name, items in slices.items():
        entry = {}
        for rule in ("anyFail", "majority"):
            entry[rule] = {f"{model}-r{run}": summary([(i, model, run) for i in items], rule)
                           for model in ("exaone", "gptoss") for run in (1, 2, 3)}
        # 짝 비교(any-fail): 엑사원 r1과 gpt-oss 각 회차
        pairs = {}
        for run in (1, 2, 3):
            c = collections.Counter()
            for i in items:
                e = turns[runs[(i, "exaone", 1)]]["anyFail"]["pass"]
                g = turns[runs[(i, "gptoss", run)]]["anyFail"]["pass"]
                c[("둘 다 통과" if e and g else "엑사원만" if e is True and g is False
                   else "gpt-oss만" if g is True and e is False else "둘 다 실패" if e is False and g is False
                   else "미확정 포함")] += 1
            pairs[f"r{run}"] = dict(c)
        entry["pairs"] = pairs
        report["slices"][name] = {"inputs": len(items), **entry}

    review = []
    for case_id, verdict in turns.items():
        case = cases[case_id]
        reasons = []
        if verdict["anyFail"]["pass"] is None:
            reasons.append("결합 판정 미확정")
        if verdict["split"]:
            reasons.append("Judge 모델끼리 갈림")
        if case["inputId"] in review_ids:
            reasons.append("라벨 검토 대상")
        if reasons:
            review.append({"caseId": case_id, "inputId": case["inputId"], "model": case["model"], "runs": case["runs"],
                           "question": case["turns"][0]["fixture"]["question"],
                           "expectedBehavior": case["turns"][0]["fixture"]["expectedBehavior"],
                           "answer": case["turns"][0]["outputMessage"]["content"],
                           "byJudge": verdict["byJudge"], "reasons": reasons})
    report["reviewCounts"] = dict(collections.Counter(r for row in review for r in row["reasons"]))
    Path(args.out).write_text(json.dumps(report, ensure_ascii=False, indent=1), encoding="utf-8")
    Path(args.out).with_name(Path(args.out).stem + "-review.json").write_text(
        json.dumps(review, ensure_ascii=False, indent=1), encoding="utf-8")
    for name in ("전체", "답할 수 있는 질문", "답 없음(ABSTAIN)", "근거 충분(엑사원 턴 기준)", "라벨 검토 대상 제외"):
        s = report["slices"][name]
        line = " | ".join(f"{key} {value['pass']['pct']}%" for key, value in s["anyFail"].items()
                          if key in ("exaone-r1", "gptoss-r1", "gptoss-r2", "gptoss-r3"))
        print(f"[{name}] 입력 {s['inputs']} | {line}")
    print("검토:", report["reviewCounts"])


if __name__ == "__main__":
    main()
