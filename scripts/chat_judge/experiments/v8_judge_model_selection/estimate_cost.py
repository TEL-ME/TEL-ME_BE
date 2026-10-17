"""기존 V7 원시 결과의 실제 토큰 사용량으로 후보 모델의 302건 채점 비용을 추정한다. Bedrock을 호출하지 않는다."""

import argparse
import json
from pathlib import Path

from scripts.chat_judge.experiments.v6_live_chat_pipeline.evaluate_chat_pipeline import load_json
from scripts.chat_judge.experiments.v7_cross_judge.analyze_human302 import DEFAULT_HUMAN, read_human
from scripts.chat_judge.experiments.v8_judge_model_selection.candidates import (
    BASELINE_PRICES, CANDIDATES, cost)


def stage_records(node):
    """결과 안의 모든 호출 기록(promptTokens가 있는 dict)을 찾는다."""
    if isinstance(node, dict):
        if "promptTokens" in node and "outputTokens" in node:
            yield node
        for value in node.values():
            yield from stage_records(value)
    elif isinstance(node, list):
        for value in node:
            yield from stage_records(value)


def usage(path, alias, keys):
    document = load_json(path)
    rows = {(row["caseId"], row["turnIndex"]): row["models"][alias]
            for row in document["turns"] if alias in row.get("models", {})}
    prompt = output = calls = 0
    for key in keys:
        for record in stage_records(rows[key]):
            prompt += record["promptTokens"] or 0
            output += record["outputTokens"] or 0
            calls += 1
    return {"turns": len(keys), "calls": calls, "promptTokens": prompt, "outputTokens": output}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--human", type=Path, default=DEFAULT_HUMAN)
    parser.add_argument("--qwen", type=Path, required=True)
    parser.add_argument("--sonnet", type=Path, required=True)
    args = parser.parse_args()
    keys = list(read_human(args.human))
    measured = {"qwen": usage(args.qwen, "qwen", keys), "sonnet-4-6": usage(args.sonnet, "sonnet-4-6", keys)}
    report = {"humanReviewedTurns": len(keys), "measured": {}, "estimate": {}}
    for alias, value in measured.items():
        value["costUsd"] = round(cost(value["promptTokens"], value["outputTokens"], BASELINE_PRICES[alias]), 4)
        report["measured"][alias] = value
    for name, candidate in CANDIDATES.items():
        report["estimate"][name] = {
            basis: {"calls": value["calls"],
                    "costUsd": round(cost(value["promptTokens"], value["outputTokens"], candidate["price"]), 4)}
            for basis, value in measured.items()}
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
