#!/usr/bin/env python3
"""모델별로 따로 판정한 결과를 턴마다 합친다. 모델은 다시 부르지 않는다.

규칙
- any-fail(기본): 한 모델이라도 실패로 보면 실패. 근거성은 한쪽이라도 근거 없음이면 근거 없음, 충족도는 가장 낮은 판정,
  보류는 한쪽이라도 지어낸 답·과도한 거절을 잡으면 그 판정. 판정하지 못한 모델이 있고 나머지가 모두 통과면 미확정
- majority: 판정한 모델의 과반이 실패면 실패, 과반이 통과면 통과, 같으면 미확정
--validation을 주면 Judge 검증셋의 사람 판정(판정자 A)과 비교한다.
사용: python -X utf8 -m scripts.chat_judge.combine_judges qwen.json.gz deepseek.json.gz [--rule any-fail] [--validation]
"""
import argparse
import collections
import json
from pathlib import Path

from scripts.chat_judge.experiments.v6_live_chat_pipeline import evaluate_chat_pipeline as pipeline

ROOT = Path(__file__).resolve().parents[2]
VALIDATION = ROOT / "scripts/chat_judge/data/judge_validation_v1.json"
QUALITY_ORDER = {"MISSED": 0, "PARTIAL": 1, "COMPLETE": 2}


def turn_labels(item):
    """한 모델의 한 턴 판정. 판정하지 못한 축은 None이다."""
    grounding = (item.get("grounding") or {}).get("result")
    quality = (item.get("quality") or {}).get("result")
    outcomes = [group["outcome"] for group in quality["groups"]] if quality else []
    if not outcomes or "REVIEW" in outcomes:
        quality_label = None
    elif all(outcome == "NOT_APPLICABLE" for outcome in outcomes):
        quality_label = "NOT_APPLICABLE"
    else:
        quality_label = min((outcome for outcome in outcomes if outcome in QUALITY_ORDER), key=QUALITY_ORDER.get)
    abstention = (item.get("abstentionDecision") or {}).get("label")
    return {"grounding": None if not grounding or grounding["overall"] == "REVIEW" else grounding["overall"],
            "quality": quality_label, "abstention": None if abstention in (None, "REVIEW") else abstention,
            "pass": item.get("answerQualityPass")}


def combine_any_fail(labels):
    values = [label["grounding"] for label in labels]
    grounding = ("UNSUPPORTED" if "UNSUPPORTED" in values else None if None in values
                 else "SUPPORTED" if "SUPPORTED" in values else "NOT_APPLICABLE")
    qualities = [label["quality"] for label in labels]
    graded = [value for value in qualities if value in QUALITY_ORDER]
    quality = (min(graded, key=QUALITY_ORDER.get) if graded else None if None in qualities else "NOT_APPLICABLE")
    abstentions = [label["abstention"] for label in labels]
    abstention = next((value for value in ("SHOULD_ABSTAIN", "OVER_REFUSAL") if value in abstentions), None)
    if abstention is None and None not in abstentions:
        abstention = "APPROPRIATE" if "APPROPRIATE" in abstentions else "NOT_APPLICABLE"
    passes = [label["pass"] for label in labels]
    verdict = False if False in passes else True if all(value is True for value in passes) else None
    return {"grounding": grounding, "quality": quality, "abstention": abstention, "pass": verdict}


def combine_majority(labels):
    passes = [label["pass"] for label in labels if label["pass"] is not None]
    failed = sum(value is False for value in passes)
    verdict = None if not passes or failed * 2 == len(passes) else failed * 2 < len(passes)
    combined = combine_any_fail(labels)
    combined["pass"] = verdict
    return combined


def load_turns(path):
    """완료된 턴만 (사례, 턴) 키로 읽는다. 검증셋 결과의 맥락용 이전 턴은 판정 대상이 아니다."""
    result = pipeline.load_json(path)
    return {(item["caseId"], item["turnIndex"]): item for item in result["turns"]
            if item.get("executionStatus") == "COMPLETED"}, result


def human_pass(label, behavior):
    quality_ok = label["quality"] == "COMPLETE" or (label["quality"] == "NOT_APPLICABLE"
                                                    and behavior in {"OUT_OF_SCOPE", "ABSTAIN"})
    return label["grounding"] != "UNSUPPORTED" and quality_ok and label["abstention"] not in {
        "SHOULD_ABSTAIN", "OVER_REFUSAL"}


def rate(numerator, denominator):
    return {"numerator": numerator, "denominator": denominator,
            "percent": round(100 * numerator / denominator, 1) if denominator else None}


def service_summary(combined, items):
    """서비스 품질셋 기준 집계. 실행 실패는 실패로 넣는다."""
    by_suite = collections.defaultdict(list)
    for key, label in combined.items():
        by_suite[items[key]["suite"]].append(label)
    def summary(labels):
        resolved = [label for label in labels if label["pass"] is not None]
        factual = [label for label in labels if label["grounding"] in {"SUPPORTED", "UNSUPPORTED"}]
        return {"turns": len(labels), "pass": rate(sum(label["pass"] is True for label in resolved), len(resolved)),
                "unresolved": len(labels) - len(resolved),
                "unsupportedAnswers": rate(sum(label["grounding"] == "UNSUPPORTED" for label in factual), len(factual))}
    return {"all": summary(list(combined.values())),
            "bySuite": {suite: summary(labels) for suite, labels in sorted(by_suite.items())}}


def validation_summary(combined):
    data = json.loads(VALIDATION.read_text(encoding="utf-8"))
    human = {item["id"]: (item["labels"]["A"], item["expectedBehavior"]) for item in data["items"]}
    # 검증 항목은 사례마다 판정한 턴이 하나다(앞 턴은 맥락). 사례 ID로 찾는다
    by_case = {key[0]: label for key, label in combined.items()}
    report = {"items": len(human)}
    for axis in ("grounding", "quality", "abstention"):
        report[axis] = sum(by_case.get(key, {}).get(axis) == label[axis] for key, (label, _) in human.items())
    passes = {key: by_case[key]["pass"] for key in human if key in by_case}
    expected = {key: human_pass(label, behavior) for key, (label, behavior) in human.items()}
    report["finalPassAgreement"] = sum(passes[key] is not None and passes[key] == expected[key] for key in passes)
    report["unresolved"] = sum(value is None for value in passes.values())
    report["passedButHumanFailed"] = sum(passes[key] is True and not expected[key] for key in passes)
    report["failedButHumanPassed"] = sum(passes[key] is False and expected[key] for key in passes)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("results", type=Path, nargs="+")
    parser.add_argument("--rule", choices=("any-fail", "majority"), default="any-fail")
    parser.add_argument("--validation", action="store_true", help="검증셋 결과일 때 사람 판정과 비교")
    parser.add_argument("--out", type=Path, help="턴별 결합 판정을 저장할 경로")
    args = parser.parse_args()
    loaded = [load_turns(path) for path in args.results]
    keys = set.intersection(*(set(turns) for turns, _ in loaded))
    rule = combine_any_fail if args.rule == "any-fail" else combine_majority
    combined = {key: rule([turn_labels(turns[key]) for turns, _ in loaded]) for key in sorted(keys)}
    items = loaded[0][0]
    report = {"models": [result.get("alias") or result.get("judgeModel") or str(path)
                         for (_, result), path in zip(loaded, args.results)], "rule": args.rule}
    report |= validation_summary(combined) if args.validation else service_summary(combined, items)
    if args.out:
        rows = [{"caseId": key[0], "turnIndex": key[1], "suite": items[key]["suite"], **label,
                 "byModel": {report["models"][index]: turn_labels(turns[key]) for index, (turns, _) in enumerate(loaded)}}
                for key, label in combined.items()]
        args.out.write_text(json.dumps({"report": report, "turns": rows}, ensure_ascii=False, indent=1) + "\n",
                            encoding="utf-8", newline="\n")
    print(json.dumps(report, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
