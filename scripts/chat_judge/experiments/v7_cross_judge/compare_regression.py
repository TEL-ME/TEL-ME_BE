"""Compare V7 model judgments with the preserved V6 human labels."""

import argparse
import json
from pathlib import Path

from scripts.chat_judge.experiments.v6_live_chat_pipeline import compare_human_review as human
from scripts.chat_judge.experiments.v6_live_chat_pipeline.evaluate_chat_pipeline import load_json
from scripts.chat_judge.experiments.v7_cross_judge.build_v7_capture import V7_DIR, V6_CAPTURE


def model_view(evaluation, model):
    return {"turns": [row["models"][model] for row in evaluation["turns"]
                      if model in row.get("models", {})]}


def compare(review_path, key_path, evaluation, model):
    gold, gold_hash = human.load_human_review(review_path, load_json(key_path))
    counts, differences = human.compare(gold, model_view(evaluation, model))
    return gold_hash, counts, differences


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--judge", type=Path, required=True)
    parser.add_argument("--model", choices=("qwen", "sonnet", "luna"), required=True)
    parser.add_argument("--human", type=Path,
        default=V6_CAPTURE.parent / "20261005-heldout40-human-review-lyj.md")
    parser.add_argument("--key", type=Path,
        default=V6_CAPTURE.parent / "20261005-heldout40-human-review-key.json")
    parser.add_argument("--out", type=Path, default=V7_DIR / "v7-regression40-comparison.md")
    args = parser.parse_args()
    gold_hash, counts, differences = compare(args.human, args.key, load_json(args.judge), args.model)
    report = human.report(args.human, gold_hash, args.judge, counts, differences)
    report = report.replace("# V6 사람 판정과 Judge 대조", f"# V7 {args.model}과 기존 40건 사람 판정 대조", 1)
    report = report.replace("선정된 검토 표본의 일치 수치다.",
        "기존 40건은 Judge 수정에 사용된 회귀 표본이다. 선정된 검토 표본의 일치 수치다.", 1)
    args.out.write_text(report, encoding="utf-8", newline="\n")
    print(json.dumps({axis: dict(value) for axis, value in counts.items()}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
