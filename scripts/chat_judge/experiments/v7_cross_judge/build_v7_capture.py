"""Add explicit question parts and all validated gold alternatives to V6 capture metadata."""

import argparse
import copy
from pathlib import Path

from scripts.chat_judge import judge_chat_flow as judge
from scripts.chat_judge.experiments.v6_live_chat_pipeline.evaluate_chat_pipeline import checkpoint, load_json


ROOT = Path(__file__).resolve().parents[4]
V6_CAPTURE = ROOT / "docs/chat-judge/experiments/V6-live-chat-pipeline/20261002-service-pipeline-capture.json.gz"
V7_DIR = ROOT / "docs/chat-judge/experiments/V7-bedrock-cross-judge"


def build_capture(capture, catalog):
    result = copy.deepcopy(capture)
    by_id = {row["slot_id"]: row for row in catalog}
    for case in result["cases"]:
        for turn in case["turns"]:
            fixture = turn["fixture"]
            references = fixture.get("qualityReferenceGroups", [])
            gold_groups = judge.gold_source_groups(fixture)
            if len(references) != len(gold_groups):
                raise ValueError(f"Missing question-part labels: {case['caseId']}")
            expanded = []
            for index, (reference, group) in enumerate(zip(references, gold_groups)):
                if reference["sourceId"] not in group:
                    raise ValueError(f"Primary reference is not gold: {case['caseId']} part {index}")
                part = fixture["question"] if len(references) == 1 else reference["question"]
                if not part.strip():
                    raise ValueError(f"Empty question part: {case['caseId']} part {index}")
                alternatives = []
                for source_id in group:
                    if source_id not in by_id:
                        raise ValueError(f"Unknown gold FAQ {source_id}")
                    alternatives.append({"sourceId": source_id, "answer": by_id[source_id]["answer"]})
                expanded.append({"questionPart": part, "alternatives": alternatives})
            fixture["qualityReferenceGroups"] = expanded
    result["v7FixtureProvenance"] = {
        "v6CaptureSha256": judge.sha256(capture),
        "catalogSha256": judge.sha256(catalog),
        "questionPartSource": "single=original question; compound=curated V6 reference question",
        "alternativesSource": "human-validated goldSourceGroups and frozen FAQ catalog",
    }
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--capture", type=Path, default=V6_CAPTURE)
    parser.add_argument("--catalog", type=Path, default=judge.DEFAULT_CATALOG)
    parser.add_argument("--out", type=Path, default=V7_DIR / "v7-capture.json.gz")
    args = parser.parse_args()
    if args.out.exists():
        parser.error("V7 input exists; preserve it and choose a new path")
    capture = build_capture(load_json(args.capture), load_json(args.catalog))
    checkpoint(args.out, capture)
    print(f"V7 fixture: {args.out} ({sum(len(c['turns']) for c in capture['cases'])} turns)")


if __name__ == "__main__":
    main()
