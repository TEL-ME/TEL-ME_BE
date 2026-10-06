"""Select 40 previously unreviewed V6 answers without looking at any Judge verdict."""

import argparse
import hashlib
import json
from pathlib import Path

from scripts.chat_judge.experiments.v6_live_chat_pipeline import evaluate_chat_pipeline as v6
from scripts.chat_judge.experiments.v6_live_chat_pipeline.prepare_human_review import format_case
from scripts.chat_judge.experiments.v7_cross_judge.build_v7_capture import V6_CAPTURE, V7_DIR
from scripts.chat_judge.experiments.v7_cross_judge.run_cross_judge import build_jobs
from scripts.chat_judge import judge_chat_flow as judge


V6_DIR = V6_CAPTURE.parent
PRIOR_KEYS = (V6_DIR / "20261005-service-pipeline-human-review-key.json",
              V6_DIR / "20261005-heldout40-human-review-key.json")
QUOTAS = {"FAQ_SINGLE": 25, "FAQ_COMPOUND": 10, "OUT_OF_SCOPE": 5}


def select(capture, catalog, prior_keys, seed="V7-blind-40"):
    excluded = {(row["caseId"], row["turnIndex"]) for document in prior_keys
                for row in document["cases"]}
    buckets = {name: [] for name in QUOTAS}
    for turn, _, item in build_jobs(capture, catalog):
        key = (item["caseId"], item["turnIndex"])
        if key in excluded or turn["executionStatus"] != "COMPLETED" or not item["answer"]:
            continue
        fixture = turn["fixture"]
        if fixture["expectedBehavior"] == "OUT_OF_SCOPE":
            bucket = "OUT_OF_SCOPE"
        elif fixture["expectedBehavior"] == "ANSWER" and len(fixture.get("qualityReferenceGroups", [])) == 2:
            bucket = "FAQ_COMPOUND"
        elif fixture["expectedBehavior"] == "ANSWER" and len(fixture.get("qualityReferenceGroups", [])) == 1:
            bucket = "FAQ_SINGLE"
        else:
            continue
        digest = hashlib.sha256(f"{seed}:{key[0]}:{key[1]}".encode()).hexdigest()
        buckets[bucket].append((digest, turn, item))
    result = []
    for bucket, count in QUOTAS.items():
        if len(buckets[bucket]) < count:
            raise ValueError(f"Only {len(buckets[bucket])} eligible cases in {bucket}")
        result.extend((bucket, turn, item) for _, turn, item in sorted(buckets[bucket])[:count])
    return result


def review_document(selected, v7_capture):
    v7_turns = {(case["caseId"], index): turn for case in v7_capture["cases"]
                for index, turn in enumerate(case["turns"])}
    sections = []
    key = []
    for index, (bucket, turn, item) in enumerate(selected, 1):
        review_id = f"V7H-{index:03d}"
        row = v7_turns[(item["caseId"], item["turnIndex"])]
        section = format_case(review_id, item, turn, include_ai_decision=False)
        alternatives = []
        for part_index, group in enumerate(row["fixture"].get("qualityReferenceGroups", []), 1):
            alternatives.append(f"- 기준 {part_index}의 질문: {group['questionPart']}")
            alternatives.extend(f"  - 대체 FAQ `{faq['sourceId']}`: {faq['answer']}"
                                for faq in group["alternatives"])
        if alternatives:
            section = section.replace("### 답변 생성에 전달된 확정 조건",
                                      "### 정답으로 인정된 FAQ 묶음\n" + "\n".join(alternatives)
                                      + "\n\n### 답변 생성에 전달된 확정 조건")
        sections.append(section)
        key.append({"reviewId": review_id, "caseId": item["caseId"],
                    "turnIndex": item["turnIndex"], "stratum": bucket})
    document = ("# V7 신규 40건 블라인드 사람 판정표\n\n"
                "이 질문은 V6에서 실제 생성한 답변이며 앞서 사람이 판정한 79건과 겹치지 않습니다. "
                "Judge 결과를 보지 않고 실제 답변, 생성에 전달된 FAQ, 질문별 대체 정답을 확인해 판정합니다. "
                "정답 FAQ는 질문 충족도 기준이며 실제 생성 근거로 소급해서 사용하지 않습니다. "
                "고정 도전 평가셋이므로 실제 사용자 질문 분포를 대표하지 않습니다.\n\n"
                "각 사례의 근거성, 질문별 충족도, 근거 충분성, 답변 불가 판단을 채우고 "
                "애매한 경우 이유와 함께 `NEEDS_HUMAN_REVIEW`로 남겨 주세요.\n\n"
                + "".join(sections))
    return document, key


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capture", type=Path, default=V6_CAPTURE)
    parser.add_argument("--v7-capture", type=Path, default=V7_DIR / "v7-capture.json.gz")
    parser.add_argument("--out", type=Path, default=V7_DIR / "v7-blind40-human-review.md")
    parser.add_argument("--key-out", type=Path, default=V7_DIR / "v7-blind40-key.json")
    args = parser.parse_args()
    if args.out.exists() or args.key_out.exists():
        parser.error("Blind review or key exists; preserve it")
    capture = v6.load_json(args.capture)
    selected = select(capture, v6.load_json(judge.DEFAULT_CATALOG),
                      [v6.load_json(path) for path in PRIOR_KEYS])
    document, key = review_document(selected, v6.load_json(args.v7_capture))
    args.out.write_text(document, encoding="utf-8", newline="\n")
    args.key_out.write_text(json.dumps({"schemaVersion": 1, "seed": "V7-blind-40",
        "v6CaptureSha256": hashlib.sha256(args.capture.read_bytes()).hexdigest(),
        "v7CaptureSha256": hashlib.sha256(args.v7_capture.read_bytes()).hexdigest(),
        "excludedPriorKeys": [str(path.name) for path in PRIOR_KEYS], "cases": key},
        ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Prepared {len(key)} blind cases: {args.out}")


if __name__ == "__main__":
    main()
