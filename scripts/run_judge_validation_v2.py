#!/usr/bin/env python3
"""500개 고유 질문으로 기본 Judge 프롬프트를 평가한다. few-shot 호출은 하지 않는다."""

import argparse
import copy
import json
from collections import Counter
from pathlib import Path

import build_judge_validation_v2 as builder
import judge_chat_flow as judge


def summarize(cases):
    axes = ("grounding", "coverage", "abstention")
    result = {
        "total": len(cases),
        "scored": sum(case.get("judgeStatus") == "SCORED" for case in cases),
        "unscored": sum(case.get("judgeStatus") == "UNSCORED" for case in cases),
        "pending": sum("judgeStatus" not in case for case in cases),
        "byAxis": {},
        "promptTokens": sum(case.get(kind, {}).get("promptTokens") or 0
                            for case in cases for kind in ("grounding", "adequacy")),
    }
    for axis in axes:
        labels = sorted({case["expected"][axis] for case in cases if case["expected"][axis] is not None})
        by_label = {}
        for label in labels:
            subset = [case for case in cases if case["expected"][axis] == label]
            scored = [case for case in subset if case.get("judgeStatus") == "SCORED"]
            correct = sum((case["grounding"]["result"]["overall"] if axis == "grounding"
                           else case["adequacy"]["result"][axis]) == label for case in scored)
            by_label[label] = {"correct": correct, "scored": len(scored), "total": len(subset)}
        result["byAxis"][axis] = {
            "correct": sum(item["correct"] for item in by_label.values()),
            "scored": sum(item["scored"] for item in by_label.values()),
            "labelled": sum(item["total"] for item in by_label.values()),
            "byLabel": by_label,
        }
    result["unscoredCaseIds"] = [case["caseId"] for case in cases if case.get("judgeStatus") == "UNSCORED"]
    return result


def reuse_adequacy(result, previous, metadata):
    required = ("datasetSha256", "catalogSha256", "judgeModel", "judgeModelDigest",
                "judgeOllamaVersion", "adequacyRubricSha256")
    if any(previous.get(key) != metadata[key] for key in required):
        raise ValueError("재사용할 충실도 판정의 평가셋 또는 모델이 다릅니다.")
    if len(previous.get("cases", [])) != len(result["cases"]):
        raise ValueError("재사용할 충실도 판정의 문항 수가 다릅니다.")
    for case, old in zip(result["cases"], previous["cases"]):
        if case["caseId"] != old.get("caseId"):
            raise ValueError("재사용할 충실도 판정의 문항 순서가 다릅니다.")
        adequacy = old.get("adequacy") or {}
        request = judge.judge_request(metadata["judgeModel"], "adequacy", case["adequacyInput"])
        if "result" not in adequacy or adequacy.get("request") != request:
            raise ValueError(f"{case['caseId']}의 충실도 판정 요청이 현재 기준과 다릅니다.")
        case["adequacy"] = copy.deepcopy(adequacy)
    result["reusedAdequacyResultSha256"] = judge.sha256(previous)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ollama-url", default="http://localhost:11435")
    parser.add_argument("--model", default=judge.MODEL)
    parser.add_argument("--out", type=Path, default=builder.ROOT / ".measure/chat-judge-validation-v2.json")
    parser.add_argument("--reuse-adequacy-from", type=Path)
    args = parser.parse_args()
    catalog = json.loads(builder.CATALOG.read_text(encoding="utf-8"))
    pilot = json.loads(builder.PILOT.read_text(encoding="utf-8"))
    v1 = json.loads(builder.V1.read_text(encoding="utf-8"))
    fewshot = json.loads(builder.FEWSHOT.read_text(encoding="utf-8"))
    dataset = json.loads(builder.OUTPUT.read_text(encoding="utf-8"))
    excluded_questions, excluded_sources = builder.excluded_questions_and_sources(pilot, v1, fewshot)
    builder.validate_dataset(dataset, catalog, excluded_questions, excluded_sources)
    metadata = {
        "schemaVersion": 1,
        "kind": "CONTROLLED_JUDGE_VALIDATION_V2_RESULT",
        "datasetSha256": judge.sha256(dataset),
        "catalogSha256": judge.sha256(catalog),
        "judgeModel": args.model,
        "judgeModelDigest": judge.model_digest(args.ollama_url, args.model),
        "judgeOllamaVersion": judge.ollama_version(args.ollama_url),
        "judgeScriptSha256": judge.sha256(Path(judge.__file__).read_bytes()),
        "groundingRubricSha256": judge.sha256(judge.GROUNDING_RUBRIC.encode("utf-8")),
        "adequacyRubricSha256": judge.sha256(judge.ADEQUACY_RUBRIC.encode("utf-8")),
        "groundingSchemaSha256": judge.sha256(judge.GROUNDING_SCHEMA),
        "adequacySchemaSha256": judge.sha256(judge.ADEQUACY_SCHEMA),
    }
    if args.out.exists():
        result = json.loads(args.out.read_text(encoding="utf-8"))
        if any(result.get(key) != value for key, value in metadata.items()):
            raise ValueError("기존 결과의 평가셋, 모델 또는 기준이 다릅니다. 새 출력 경로를 사용하세요.")
        if [case["caseId"] for case in result["cases"]] != [case["caseId"] for case in dataset["cases"]]:
            raise ValueError("기존 결과의 질문 순서가 다릅니다.")
    else:
        result = {**metadata, "cases": dataset["cases"]}
        if args.reuse_adequacy_from:
            previous = json.loads(args.reuse_adequacy_from.read_text(encoding="utf-8"))
            reuse_adequacy(result, previous, metadata)

    def checkpoint():
        result["summary"] = summarize(result["cases"])
        args.out.parent.mkdir(parents=True, exist_ok=True)
        temporary = args.out.with_suffix(args.out.suffix + ".tmp")
        temporary.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        temporary.replace(args.out)

    for index, case in enumerate(result["cases"], 1):
        if case.get("judgeStatus") in {"SCORED", "UNSCORED"}:
            continue
        for kind in ("grounding", "adequacy"):
            if kind in case:
                continue
            try:
                case[kind] = judge.ollama_chat(args.ollama_url, args.model, kind,
                                               case[kind + "Input"])
            except judge.JudgeCallError as error:
                case[kind] = error.record
            checkpoint()
        case["judgeStatus"] = "SCORED" if all(
            "result" in case[kind] for kind in ("grounding", "adequacy")) else "UNSCORED"
        checkpoint()
        print(f"[{index}/{len(result['cases'])}] {case['caseId']}: {case['judgeStatus']}", flush=True)
    print(json.dumps(result["summary"], ensure_ascii=False, indent=2))
    print(f"결과: {args.out}")


if __name__ == "__main__":
    main()
