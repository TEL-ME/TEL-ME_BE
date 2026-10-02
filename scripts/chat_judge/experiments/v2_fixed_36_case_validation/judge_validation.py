#!/usr/bin/env python3
"""실제 FAQ에 기반한 고정 답변으로 Judge의 판정 정확도를 독립 검증한다."""

import argparse
import json
from collections import Counter
from pathlib import Path

from scripts.chat_judge import judge_chat_flow as judge


ROOT = Path(__file__).resolve().parents[4]
FIXTURE = ROOT / "scripts/chat_judge/data/chat_judge_validation_v1.json"
PILOT = ROOT / "scripts/chat_judge/data/chat_judge_pilot.json"
FEWSHOT = judge.DEFAULT_FEWSHOT
REFUSAL = "현재 확인할 수 없어 안내드리기 어렵습니다."
OUT_OF_SCOPE_ANSWER = "통신 서비스 관련 질문을 해주세요."
POLICY_LABELS = {
    "complete": ("SUPPORTED", "COMPLETE", "NOT_APPLICABLE"),
    "partial": ("SUPPORTED", "PARTIAL", "NOT_APPLICABLE"),
    "unsupportedExtra": ("UNSUPPORTED", "PARTIAL", "SHOULD_ABSTAIN"),
    "wrong": ("UNSUPPORTED", "MISSED", "SHOULD_ABSTAIN"),
    "refusalWithSource": ("NOT_APPLICABLE", "MISSED", "OVER_REFUSAL"),
    "refusalWithoutSource": ("NOT_APPLICABLE", "MISSED", "APPROPRIATE"),
}


def make_case(case_id, question, answer, required_facts, source_id, source, variant, expected_behavior="ANSWER"):
    grounding, coverage, abstention = POLICY_LABELS[variant] if variant in POLICY_LABELS else (
        "NOT_APPLICABLE", "NOT_APPLICABLE", "APPROPRIATE")
    if variant == "outOfScope":
        if source_id is not None or source is not None:
            raise ValueError("범위 밖 질문에 FAQ 근거가 있습니다.")
    elif source_id is None:
        raise ValueError("통신 질문의 정답 FAQ가 없습니다.")
    actual = [] if variant in {"refusalWithoutSource", "outOfScope"} else [source]
    basis = "OUT_OF_SCOPE" if variant == "outOfScope" else (
        "NO_EVIDENCE" if variant.startswith("refusal") else "GROUNDED")
    common = {
        "question": question,
        "previousTurns": [],
        "answer": answer,
        "answerBasis": basis,
        "messageType": "ANSWER",
        "sources": actual,
    }
    return {
        "caseId": case_id,
        "variant": variant,
        "expected": {"grounding": grounding, "coverage": coverage, "abstention": abstention},
        "groundingInput": {**common, "confirmedConditions": []},
        "adequacyInput": {
            **common,
            "expectedBehavior": expected_behavior,
            "requiredFacts": required_facts,
            "missingFact": None,
            "goldSourceSlotIds": [source_id] if source_id else [],
            "actualSourceSlotIds": [item["sourceId"] for item in actual],
        },
    }


def build_cases(fixture, catalog, pilot, fewshot):
    if fixture.get("schemaVersion") != 1 or fixture.get("name") != "judge_validation_v1":
        raise ValueError("지원하지 않는 검증셋입니다.")
    lookup = {faq["slot_id"]: faq for faq in catalog}
    cases = []
    seen_questions = set()
    def source_for(item):
        slot_id = item["sourceSlotId"]
        faq = lookup.get(slot_id)
        if faq is None:
            raise ValueError(f"FAQ 원본에 없는 ID입니다: {slot_id}")
        return {"sourceId": slot_id, "faqId": None,
                "question": faq["question"], "answer": faq["answer"], "rank": 1}

    for policy in fixture["policies"]:
        if set(policy["answers"]) != {"complete", "partial", "unsupportedExtra", "wrong"}:
            raise ValueError(f"필수 답변 변형이 없습니다: {policy['id']}")
        source = source_for(policy)
        seen_questions.add(policy["question"])
        for variant, answer in policy["answers"].items():
            cases.append(make_case(f"{policy['id']}_{variant}", policy["question"], answer,
                                   policy["requiredFacts"], policy["sourceSlotId"], source, variant))
        for variant, present in (("refusalWithSource", True), ("refusalWithoutSource", False)):
            cases.append(make_case(f"{policy['id']}_{variant}", policy["question"], REFUSAL,
                                   policy["requiredFacts"], policy["sourceSlotId"],
                                   source if present else None, variant))
    for policy in fixture["simplePolicies"]:
        source = source_for(policy)
        seen_questions.add(policy["question"])
        for variant, answer in (("complete", policy["answer"]), ("refusalWithSource", REFUSAL)):
            cases.append(make_case(f"{policy['id']}_{variant}", policy["question"], answer,
                                   policy["requiredFacts"], policy["sourceSlotId"], source, variant))
    for item in fixture["outOfScope"]:
        seen_questions.add(item["question"])
        cases.append(make_case(item["id"] + "_outOfScope", item["question"], OUT_OF_SCOPE_ANSWER,
                               [], None, None, "outOfScope", "OUT_OF_SCOPE"))
    ids = [case["caseId"] for case in cases]
    if len(ids) != len(set(ids)) or len(seen_questions) != (len(fixture["policies"])
                                                      + len(fixture["simplePolicies"])
                                                      + len(fixture["outOfScope"])):
        raise ValueError("검증셋 ID 또는 질문이 중복됐습니다.")
    used_questions = {turn["question"] for item in pilot for turn in item["turns"]}
    used_questions.update(example["input"]["question"] for kind in ("grounding", "adequacy")
                          for example in fewshot[kind])
    if seen_questions & used_questions:
        raise ValueError("파일럿 또는 few-shot 예시와 질문이 겹칩니다.")
    for axis in ("grounding", "coverage", "abstention"):
        counts = Counter(case["expected"][axis] for case in cases)
        if min(counts.values()) < 4 or len(counts) < (3 if axis == "grounding" else 4):
            raise ValueError(f"{axis}의 판정별 검증 문항이 부족합니다: {counts}")
    return cases


def summarize(cases):
    result = {"total": len(cases), "scored": {}, "agreement": {}, "byLabel": {},
              "inputTokens": {}, "unscoredCaseIds": {}}
    for mode in ("baseline", "fewshot"):
        result["scored"][mode] = sum(case.get(mode, {}).get("judgeStatus") == "SCORED" for case in cases)
        result["unscoredCaseIds"][mode] = [case["caseId"] for case in cases
                                            if case.get(mode, {}).get("judgeStatus") == "UNSCORED"]
        result["inputTokens"][mode] = sum(
            case.get(mode, {}).get(kind, {}).get("promptTokens") or 0
            for case in cases for kind in ("grounding", "adequacy")
        )
        result["agreement"][mode] = {}
        result["byLabel"][mode] = {}
        for axis in ("grounding", "coverage", "abstention"):
            expected = Counter(case["expected"][axis] for case in cases)
            correct = Counter()
            scored = Counter()
            for case in cases:
                judged = case.get(mode, {})
                if judged.get("judgeStatus") != "SCORED":
                    continue
                scored[case["expected"][axis]] += 1
                actual = judged["grounding"]["result"]["overall"] if axis == "grounding" else (
                    judged["adequacy"]["result"][axis])
                if actual == case["expected"][axis]:
                    correct[actual] += 1
            result["agreement"][mode][axis] = sum(correct.values())
            result["byLabel"][mode][axis] = {
                label: {"correct": correct[label], "scored": scored[label], "total": count}
                for label, count in sorted(expected.items())
            }
    common = [case for case in cases if all(case.get(mode, {}).get("judgeStatus") == "SCORED"
                                             for mode in ("baseline", "fewshot"))]
    result["bothScored"] = {"total": len(common), "agreement": {}}
    for mode in ("baseline", "fewshot"):
        result["bothScored"]["agreement"][mode] = {
            axis: sum((case[mode]["grounding"]["result"]["overall"] if axis == "grounding"
                       else case[mode]["adequacy"]["result"][axis]) == case["expected"][axis]
                      for case in common)
            for axis in ("grounding", "coverage", "abstention")
        }
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ollama-url", default="http://localhost:11435")
    parser.add_argument("--model", default=judge.MODEL)
    parser.add_argument("--out", type=Path, default=ROOT / ".measure/chat-judge-validation-v1.json")
    args = parser.parse_args()
    fixture = json.loads(FIXTURE.read_text(encoding="utf-8"))
    pilot = json.loads(PILOT.read_text(encoding="utf-8"))
    fewshot = judge.validate_fewshot(json.loads(FEWSHOT.read_text(encoding="utf-8")))
    catalog = json.loads(judge.DEFAULT_CATALOG.read_text(encoding="utf-8"))
    cases = build_cases(fixture, catalog, pilot, fewshot)
    metadata = {
        "schemaVersion": 1,
        "kind": "CONTROLLED_JUDGE_VALIDATION",
        "fixtureSha256": judge.sha256(fixture),
        "catalogSha256": judge.sha256(catalog),
        "fewshotSha256": judge.sha256(fewshot),
        "groundingRubricSha256": judge.sha256(judge.GROUNDING_RUBRIC.encode("utf-8")),
        "adequacyRubricSha256": judge.sha256(judge.ADEQUACY_RUBRIC.encode("utf-8")),
        "judgeModel": args.model,
        "judgeModelDigest": judge.model_digest(args.ollama_url, args.model),
        "judgeOllamaVersion": judge.ollama_version(args.ollama_url),
    }
    if args.out.exists():
        result = json.loads(args.out.read_text(encoding="utf-8"))
        if any(result.get(key) != value for key, value in metadata.items()):
            raise ValueError("기존 결과의 입력 또는 모델 정보가 다릅니다. 새 출력 경로를 사용하세요.")
        if [case["caseId"] for case in result["cases"]] != [case["caseId"] for case in cases]:
            raise ValueError("기존 결과의 평가 항목이 다릅니다.")
    else:
        result = {**metadata, "cases": cases}
    def checkpoint():
        result["summary"] = summarize(result["cases"])
        args.out.parent.mkdir(parents=True, exist_ok=True)
        temporary = args.out.with_suffix(args.out.suffix + ".tmp")
        temporary.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        temporary.replace(args.out)
    for case in result["cases"]:
        for mode in ("baseline", "fewshot"):
            judged = case.setdefault(mode, {})
            if judged.get("judgeStatus") in {"SCORED", "UNSCORED"}:
                continue
            failed = False
            for kind in ("grounding", "adequacy"):
                if kind in judged:
                    continue
                data = case[kind + "Input"]
                try:
                    judged[kind] = judge.ollama_chat(args.ollama_url, args.model, kind, data,
                                                     examples=fewshot[kind] if mode == "fewshot" else None)
                except judge.JudgeCallError as error:
                    judged[kind] = error.record
                    failed = True
                checkpoint()
            judged["judgeStatus"] = "UNSCORED" if failed or any(
                "result" not in judged.get(kind, {}) for kind in ("grounding", "adequacy")) else "SCORED"
            checkpoint()
        print(case["caseId"], result["summary"]["scored"], flush=True)
    print(json.dumps(result["summary"], ensure_ascii=False, indent=2))
    print(f"결과: {args.out}")


if __name__ == "__main__":
    main()
