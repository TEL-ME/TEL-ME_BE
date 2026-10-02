#!/usr/bin/env python3
"""동일한 생성 답변에 대한 두 Judge 결과를 수동 판정과 비교한다."""

import argparse
import json
from pathlib import Path


AXES = ("grounding", "coverage", "abstention", "finalAbstention")


def flatten(evaluation):
    return {
        (case["caseId"], index): turn
        for case in evaluation["cases"]
        for index, turn in enumerate(case["turns"])
    }


def labels(turn):
    return {
        "grounding": turn["grounding"]["result"]["overall"],
        "coverage": turn["adequacy"]["result"]["coverage"],
        "abstention": turn["adequacy"]["result"]["abstention"],
        "finalAbstention": turn["abstentionDecision"]["label"],
    }


def compare(manual, baseline, candidate):
    for field in ("generatorCaptureSha256", "sourceCatalogSha256", "judgeModelDigest",
                  "groundingRubricSha256", "adequacyRubricSha256"):
        if baseline.get(field) != candidate.get(field):
            raise ValueError(f"동일한 평가 조건이 아닙니다: {field}")
    expected = {(item["caseId"], item["turnIndex"]): item for item in manual["turns"]}
    if len(expected) != len(manual["turns"]):
        raise ValueError("수동 판정의 질문 ID가 중복됐습니다.")
    old, new = flatten(baseline), flatten(candidate)
    if expected.keys() != old.keys() or expected.keys() != new.keys():
        raise ValueError("비교할 질문이 서로 다릅니다.")
    counts = {name: {axis: 0 for axis in AXES} for name in ("baseline", "candidate")}
    disagreements = []
    for key, human in expected.items():
        old_turn, new_turn = old[key], new[key]
        if old_turn["judgeStatus"] != "SCORED" or new_turn["judgeStatus"] != "SCORED":
            raise ValueError(f"채점되지 않은 질문이 있습니다: {key}")
        if old_turn["question"] != new_turn["question"]:
            raise ValueError(f"질문 원문이 다릅니다: {key}")
        old_labels, new_labels = labels(old_turn), labels(new_turn)
        for axis in AXES:
            gold = human["abstention"] if axis == "finalAbstention" else human[axis]
            counts["baseline"][axis] += old_labels[axis] == gold
            counts["candidate"][axis] += new_labels[axis] == gold
            if old_labels[axis] != gold or new_labels[axis] != gold:
                disagreements.append({"caseId": key[0], "turnIndex": key[1], "axis": axis,
                                      "human": gold, "baseline": old_labels[axis], "candidate": new_labels[axis]})
    def tokens(evaluation):
        return sum(turn[kind].get("promptTokens") or 0 for turn in flatten(evaluation).values()
                   for kind in ("grounding", "adequacy"))
    return {
        "captureSha256": baseline["generatorCaptureSha256"],
        "modelDigest": baseline["judgeModelDigest"],
        "turns": len(expected),
        "matchingHuman": counts,
        "inputTokens": {"baseline": tokens(baseline), "candidate": tokens(candidate)},
        "disagreements": disagreements,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manual", type=Path)
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    inputs = [json.loads(path.read_text(encoding="utf-8"))
              for path in (args.manual, args.baseline, args.candidate)]
    result = compare(*inputs)
    output = json.dumps(result, ensure_ascii=False, indent=2)
    if args.out:
        args.out.write_text(output + "\n", encoding="utf-8")
    print(output)


if __name__ == "__main__":
    main()
