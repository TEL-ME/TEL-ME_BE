"""후보 모델과 기존 V7 모델의 채점 결과를 사람 판정 302건과 비교한다. Bedrock을 호출하지 않는다."""

import argparse
import json
from collections import Counter
from pathlib import Path

from scripts.chat_judge.experiments.v7_cross_judge.analyze_human302 import (
    DEFAULT_HUMAN, DEFERRED, answer_label, read_human, read_model)


def agreement(human, rows, keys, axis, single):
    counts = Counter()
    for key in keys:
        value = answer_label(rows[key], axis, single)
        actual = human[key][axis]
        if value in DEFERRED:
            counts["unresolved"] += 1
            continue
        counts["resolved"] += 1
        counts["match" if value == actual else "mismatch"] += 1
        if axis == "grounding" and value != actual:
            if actual == "UNSUPPORTED" and value == "SUPPORTED":
                counts["missedUnsupported"] += 1
            if actual == "SUPPORTED" and value == "UNSUPPORTED":
                counts["falseAlarm"] += 1
    return dict(counts)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--human", type=Path, default=DEFAULT_HUMAN)
    parser.add_argument("--model", action="append", required=True, metavar="ALIAS=PATH",
                        help="예: mistral=mistral-raw.json.gz, qwen=v7-qwen-final-raw.json.gz")
    args = parser.parse_args()
    human = read_human(args.human)
    keys = list(human)
    models = {}
    for item in args.model:
        alias, path = item.split("=", 1)
        _, models[alias] = read_model(Path(path), alias)
        missing = set(keys) - set(models[alias])
        if missing:
            raise SystemExit(f"{alias} 결과에 사람 판정 {len(missing)}건이 없습니다")
    single = [key for key in keys if len(((next(iter(models.values()))[key].get("quality", {})
                                           .get("result") or {}).get("groups")) or []) == 1]
    report = {}
    for alias, rows in models.items():
        report[alias] = {
            "grounding": agreement(human, rows, keys, "grounding", True),
            "qualitySingleQuestion": agreement(human, rows, single, "quality", True),
            "abstention": agreement(human, rows, keys, "abstention", True),
        }
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
