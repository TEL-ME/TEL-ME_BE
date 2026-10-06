"""Prepare a blind human-review form for the untouched V6 holdout cases."""

import argparse
import hashlib
import json
from pathlib import Path

from scripts.chat_judge.experiments.v6_live_chat_pipeline.evaluate_chat_pipeline import load_json
from scripts.chat_judge.experiments.v6_live_chat_pipeline.prepare_human_review import format_case


ROOT = Path(__file__).resolve().parents[4]
EXPERIMENT = ROOT / "docs/chat-judge/experiments/V6-live-chat-pipeline"


def build_review(cases, capture, evaluation):
    captured = {(case["caseId"], index): turn
                for case in capture["cases"] for index, turn in enumerate(case["turns"])}
    judged = {(turn["caseId"], turn["turnIndex"]): turn for turn in evaluation["turns"]}
    if len(cases) != 40 or len(captured) != 40 or len(judged) != 40:
        raise ValueError("Expected exactly 40 distinct holdout cases and results")
    if len({case["caseId"] for case in cases}) != 40:
        raise ValueError("Holdout case IDs are not unique")

    sections = []
    key = []
    index_rows = []
    for index, case in enumerate(cases, start=1):
        case_id = case["caseId"]
        item_key = (case_id, 0)
        if item_key not in captured or item_key not in judged:
            raise ValueError(f"Missing holdout case: {case_id}")
        turn = judged[item_key]
        source = captured[item_key]
        if turn["executionStatus"] != "COMPLETED":
            raise ValueError(f"Holdout answer was not completed: {case_id}")
        review_id = f"V6H-{index:03d}"
        section = format_case(review_id, turn, source, include_ai_decision=False)
        section = section.replace(
            "- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):",
            "- 사람이 본 근거 충분성 (`ENOUGH` / `INSUFFICIENT`):\n"
            "- 별도 파이프라인 실패 유형 (`NONE` / `MISROUTED_CLARIFICATION` / `OTHER` / `NEEDS_HUMAN_REVIEW`):",
        )
        sections.append(section)
        question = source["fixture"]["question"].replace("|", "\\|").replace("\n", " ")
        index_rows.append(f"| [{review_id}](#{review_id.lower()}) | {question} |")
        key.append({"reviewId": review_id, "caseId": case_id, "turnIndex": 0})
    if set(captured) != set(judged) or set(captured) != {(case["caseId"], 0) for case in cases}:
        raise ValueError("Capture and Judge holdout sets differ")

    document = """# V6 별도 40건 사람 판정표

이 40건은 앞서 직접 판정한 39건과 겹치지 않는 고정 평가 질문입니다. 실제 사용자의 무작위 질문 표본은 아닙니다. 이 문서에는 Judge의 판정과 변경 여부를 넣지 않았습니다.

각 사례에서 **실제 저장 답변**을 **답변 생성에 전달된 실제 근거**와 비교해 아래 판정 칸을 채워주세요. 참고 FAQ 기준은 질문 충족도를 확인하는 용도이며, 실제 답변에 전달된 근거로 간주하지 않습니다.

- 근거성: 답변 속 사실 주장이 모두 실제 근거에 있으면 `SUPPORTED`, 하나라도 근거 밖이면 `UNSUPPORTED`, 사실 주장이 없으면 `NOT_APPLICABLE`.
- 질문 충족도: 하위 질문마다 `COMPLETE`, `PARTIAL`, `MISSED`, `NOT_APPLICABLE` 중 하나.
- 답변 불가: 근거 부족 시 적절히 보류했으면 `APPROPRIATE`, 근거 밖 사실을 단정했으면 `SHOULD_ABSTAIN`, 답할 근거가 있는데 거절했으면 `OVER_REFUSAL`, 정상 답변이면 `NOT_APPLICABLE`.
- 실제 근거 충분성: 실제 근거만으로 질문에 답할 수 있으면 `ENOUGH`, 아니면 `INSUFFICIENT`.
- 별도 파이프라인 실패 유형은 답변의 사실성·질문 충족도와 별도로 기록합니다. 질문 의도와 다른 유형으로 잘못 분류해 엉뚱한 확인 질문을 했다면 `MISROUTED_CLARIFICATION`, 다른 파이프라인 오류면 `OTHER`, 해당 오류가 없으면 `NONE`을 적습니다. 애매하면 `NEEDS_HUMAN_REVIEW`로 둡니다.
- 애매하면 억지로 고르지 말고 `NEEDS_HUMAN_REVIEW`와 이유를 메모에 적어주세요. 사람 판정을 다 기록한 뒤 Judge 결과와 대조합니다.

각 `사람 판정 기록`의 빈 줄을 아래처럼 바꿔 적어주세요. 하위 질문이 둘이면 `기준 1`, `기준 2`를 각각 채웁니다.

```text
- 근거성 전체 판정: `SUPPORTED`
- 주장별 판정과 근거 ID: 답변의 어떤 주장을 어떤 FAQ가 뒷받침하는지 간단히 기록
- 질문 충족도:
  - 기준 1: `COMPLETE`
- 답변 불가 판정: `NOT_APPLICABLE`
- 실제 전달 근거 충분성: `ENOUGH`
- 별도 파이프라인 실패 유형: `NONE`
- 메모: 판단 이유나 애매한 부분
```

## 질문 찾아가기

| 번호 | 질문 |
| --- | --- |
""" + "\n".join(index_rows) + """

---

""" + "".join(sections)
    return document, key


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=EXPERIMENT / "20261005-heldout40-human-review.md")
    parser.add_argument("--key-out", type=Path, default=EXPERIMENT / "20261005-heldout40-human-review-key.json")
    args = parser.parse_args()

    cases_path = EXPERIMENT / "20261005-heldout40-cases.json"
    capture_path = EXPERIMENT / "20261005-heldout40-capture.json.gz"
    judge_path = EXPERIMENT / "20261005-heldout40-judge-updated-raw.json.gz"
    document, key = build_review(load_json(cases_path), load_json(capture_path), load_json(judge_path))
    args.out.write_text(document, encoding="utf-8", newline="\n")
    args.key_out.write_text(json.dumps({
        "schemaVersion": 1,
        "capture": capture_path.name,
        "captureSha256": hashlib.sha256(capture_path.read_bytes()).hexdigest(),
        "judge": judge_path.name,
        "judgeSha256": hashlib.sha256(judge_path.read_bytes()).hexdigest(),
        "cases": key,
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"Prepared {len(key)} blind holdout cases: {args.out}")


if __name__ == "__main__":
    main()
