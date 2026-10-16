#!/usr/bin/env python3
"""Judge 검증셋(judge_validation_v1.json)의 고정 답변을 Judge로 판정하고 사람 판정과 축별로 비교한다.

답변은 다시 만들지 않는다. 판정은 실제 파이프라인 평가와 같은 evaluate()를 쓴다. 판정자 B의 판정표가 채워지면
--review-b로 넘겨 A·B 간 일치도와 B 기준 일치율도 함께 낸다.
사용: python -X utf8 -m scripts.chat_judge.run_judge_validation --out .measure/judge-validation-<날짜>.json.gz
      python -X utf8 -m scripts.chat_judge.run_judge_validation --judged <기존 결과> --review-b docs/chat-judge/judge-validation-v1-review-form.md
"""
import argparse
import collections
import json
import re
from pathlib import Path

from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge.experiments.v6_live_chat_pipeline import evaluate_chat_pipeline as pipeline

ROOT = Path(__file__).resolve().parents[2]
DATASET = ROOT / "scripts/chat_judge/data/judge_validation_v1.json"
AXES = ("grounding", "quality", "abstention")


def to_capture(items):
    """검증 항목을 Judge 입력 형식으로 바꾼다. 생성에 전달된 FAQ 답변만 근거로 넣고 FAQ 질문 문구는 비운다.
    이전 턴은 질문과 답변만 판정 맥락으로 남기고 판정하지 않는다."""
    cases = []
    for item in items:
        turns = [{"fixture": {"question": previous["question"], "expectedIntent": "FAQ", "expectedBehavior": "ANSWER",
                              "goldSourceGroups": [], "requiredFacts": []},
                  "executionStatus": "CANCELLED", "searches": [],
                  "outputMessage": {"content": previous["answer"], "messageType": "ANSWER"}}
                 for previous in item["previousTurns"]]
        results = [{"faqId": 0, "slotId": source["sourceId"], "question": "", "answer": source["answer"]}
                   for source in item["deliveredSources"]]
        turns.append({"fixture": {"question": item["question"], "expectedIntent": "FAQ",
                                  "expectedBehavior": item["expectedBehavior"], "goldSourceGroups": [],
                                  "requiredFacts": [], "qualityReferenceGroups": item["referenceGroups"]},
                      "executionStatus": "COMPLETED", "searches": [{"query": item["question"], "results": results}],
                      "ragInputs": [{"searchResults": results}],
                      "outputMessage": {"content": item["answer"], "messageType": item["messageType"]}})
        cases.append({"caseId": item["id"], "category": item["category"], "suite": item["stratum"], "turns": turns})
    return {"schemaVersion": 1, "cases": cases, "plannedTurnCount": len(items)}


def judge_labels(evaluation):
    """Judge 결과에서 사람 판정과 같은 세 코드(근거성 전체, 충족도, 최종 보류)를 꺼낸다. 판정 불가는 None."""
    labels = {}
    for item in evaluation["turns"]:
        if item.get("executionStatus") != "COMPLETED":
            continue
        grounding = (item.get("grounding") or {}).get("result")
        quality = (item.get("quality") or {}).get("result")
        groups = quality["groups"] if quality else []
        labels[item["caseId"]] = {
            "grounding": grounding["overall"] if grounding else None,
            "quality": groups[0]["outcome"] if len(groups) == 1 else None,
            "abstention": (item.get("abstentionDecision") or {}).get("label"),
        }
    return labels


def review_b(path):
    """판정자 B가 채운 판정표에서 세 축을 읽는다. 비어 있는 항목은 건너뛴다."""
    labels, current = {}, None
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        header = re.match(r"^## \d+\. `(?P<id>[^`]+)`", line)
        if header:
            current = header["id"]
            continue
        field = re.match(r"^- (근거성|충족도|보류):\s*`?(?P<value>[A-Z_]*)`?\s*$", line)
        if current and field and field["value"]:
            axis = {"근거성": "grounding", "충족도": "quality", "보류": "abstention"}[field[1]]
            labels.setdefault(current, {})[axis] = field["value"]
    return {key: value for key, value in labels.items() if len(value) == len(AXES)}


def compare(reference, candidate, ids):
    report = {}
    for axis in AXES:
        pairs = [(reference[i][axis], candidate[i][axis]) for i in ids if i in reference and i in candidate]
        resolved = [(a, b) for a, b in pairs if b not in {None, "REVIEW"}]
        confusion = collections.defaultdict(collections.Counter)
        for a, b in resolved:
            confusion[a][b] += 1
        report[axis] = {"compared": len(pairs), "resolved": len(resolved),
                        "match": sum(a == b for a, b in resolved), "unresolved": len(pairs) - len(resolved),
                        "confusion": {key: dict(value) for key, value in confusion.items()}}
    grounding = [(reference[i]["grounding"], candidate[i]["grounding"]) for i in ids if i in reference and i in candidate]
    report["missedUnsupported"] = sum(a == "UNSUPPORTED" and b == "SUPPORTED" for a, b in grounding)
    report["falseUnsupported"] = sum(a == "SUPPORTED" and b == "UNSUPPORTED" for a, b in grounding)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--out", type=Path, help="새 Judge 판정 결과(.json.gz)")
    parser.add_argument("--judged", type=Path, help="이미 판정한 결과를 다시 비교만 할 때")
    parser.add_argument("--review-b", type=Path, help="판정자 B가 채운 판정표")
    parser.add_argument("--url", default="http://localhost:8001")
    parser.add_argument("--model", default="qwen3-14b-awq")
    parser.add_argument("--workers", type=int, default=8)
    args = parser.parse_args()
    data = json.loads(DATASET.read_text(encoding="utf-8"))
    items = data["items"]
    if args.judged:
        evaluation = pipeline.load_json(args.judged)
    else:
        if not args.out or args.out.exists():
            parser.error("새 결과 경로 --out이 필요하고 기존 파일을 덮어쓰지 않는다")
        catalog = json.loads(judge.DEFAULT_CATALOG.read_text(encoding="utf-8"))
        evaluation = pipeline.evaluate(to_capture(items), catalog, args.url, args.model, workers=args.workers,
                                       on_checkpoint=lambda value: pipeline.checkpoint(args.out, value))
        evaluation["datasetSha256"] = judge.sha256(data)
        pipeline.checkpoint(args.out, evaluation)
    ids = [item["id"] for item in items]
    human_a = {item["id"]: item["labels"]["A"] for item in items}
    model = judge_labels(evaluation)
    result = {"items": len(ids), "judgeVsA": compare(human_a, model, ids)}
    if args.review_b:
        human_b = review_b(args.review_b)
        result["reviewedByB"] = len(human_b)
        result["aVsB"] = compare(human_a, human_b, ids)
        result["judgeVsB"] = compare(human_b, model, ids)
    print(json.dumps(result, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
