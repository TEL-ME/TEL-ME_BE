"""Export all model disagreements and unscored cases for manual adjudication."""

import argparse
from pathlib import Path

from scripts.chat_judge.experiments.v6_live_chat_pipeline.evaluate_chat_pipeline import load_json
from scripts.chat_judge.experiments.v7_cross_judge.build_v7_capture import V7_DIR


def report(evaluation):
    lines = ["# V7 교차 판정 사람 검토 목록", "",
             "세 모델이 모두 유효하게 같은 결론을 낸 사례만 자동 확정합니다. "
             "한 모델이라도 누락, 미채점, REVIEW 또는 불일치가 있으면 아래에 남깁니다.", "",
             "| 사례 | 질문 | 근거성 | 질문 충족도 | 답변 불가 | 오류 |",
             "| --- | --- | --- | --- | --- | --- |"]
    for row in evaluation["turns"]:
        if row.get("consensus", {}).get("status") != "HUMAN_REVIEW":
            continue
        def outcome(model, axis):
            item = row.get("models", {}).get(model, {})
            record = item.get(axis, {})
            if "error" in record:
                return "미채점"
            result = record.get("result") or {}
            if axis == "grounding":
                return result.get("overall", "미채점")
            if axis == "quality":
                return "/".join(group["outcome"] for group in result.get("groups", [])) or "미채점"
            return (item.get("abstentionDecision") or {}).get("label", "미채점")
        cells = []
        for axis in ("grounding", "quality", "abstention"):
            cells.append("<br>".join(f"{model}: {outcome(model, axis)}" for model in ("qwen", "sonnet", "luna")))
        errors = "; ".join(f"{model}/{axis}: {record['error']}" for model, item in row.get("models", {}).items()
                           for axis in ("grounding", "quality", "abstention")
                           if (record := item.get(axis, {})).get("error"))
        escaped = lambda value: str(value).replace("|", "\\|").replace("\n", " ")
        lines.append(f"| {row['caseId']}:{row['turnIndex'] + 1} | {escaped(row['question'])} | "
                     + " | ".join(escaped(cell) for cell in cells)
                     + f" | {escaped(errors)} |")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--judge", type=Path, default=V7_DIR / "v7-bedrock-judged-raw.json.gz")
    parser.add_argument("--out", type=Path, default=V7_DIR / "v7-human-review-queue.md")
    args = parser.parse_args()
    args.out.write_text(report(load_json(args.judge)), encoding="utf-8", newline="\n")
    print(args.out)


if __name__ == "__main__":
    main()
