"""Build real-chat questions without injecting synthetic answers or Judge labels."""

import argparse
import copy
import json
from pathlib import Path

from scripts.chat_judge import judge_chat_flow as judge


ROOT = Path(__file__).resolve().parents[4]
DEFAULT_OUTPUT = ROOT / "scripts/chat_judge/data/chat_pipeline_quality_v1.json"


def build_cases(validation, pilot, catalog):
    by_id = {faq["slot_id"]: faq for faq in catalog}
    cases = []
    for original in validation["cases"]:
        data = original["adequacyInput"]
        outside = original["behavior"] == "OUT_OF_SCOPE"
        groups = copy.deepcopy(data.get("goldSourceGroups", []))
        references = []
        for group in groups:
            primary = next((sid for sid in data["goldSourceSlotIds"] if sid in group), group[0])
            faq = by_id[primary]
            references.append({"question": faq["question"], "sourceId": primary, "answer": faq["answer"]})
        turn = {
            "question": data["question"],
            "expectedIntent": "UNKNOWN" if outside else "FAQ",
            "expectedBehavior": "OUT_OF_SCOPE" if outside else "ANSWER",
            "goldSourceGroups": groups,
            # The full FAQ answer is a reference, not a list of mandatory extra facts.
            "requiredFacts": [],
            "qualityReferenceGroups": references,
            "labelProvenance": "catalog_reference; synthetic_answer_and_expected_judge_labels_removed",
        }
        cases.append({"caseId": "PIPE-" + original["caseId"], "category": original["category"],
                      "questionType": original["questionType"], "suite": "catalog_questions",
                      "turns": [turn]})
    for original in pilot:
        case = copy.deepcopy(original)
        case["caseId"] = "REG-" + case["caseId"]
        case["suite"] = "flow_regression"
        case["questionType"] = case["caseId"].removeprefix("REG-")
        for turn in case["turns"]:
            turn["labelProvenance"] = "curated_flow_fixture"
            turn["qualityReferenceGroups"] = []
            for group in judge.gold_source_groups(turn):
                primary = next((sid for sid in turn.get("goldSourceSlotIds", []) if sid in group), group[0])
                faq = by_id[primary]
                turn["qualityReferenceGroups"].append(
                    {"question": faq["question"], "sourceId": primary, "answer": faq["answer"]})
        cases.append(case)
    if len({case["caseId"] for case in cases}) != len(cases):
        raise ValueError("Duplicate pipeline case ID")
    return cases


def select_cases(cases, limit=0):
    """Keep flow cases, then round-robin categories and question types."""
    if limit <= 0 or limit >= len(cases):
        return copy.deepcopy(cases)
    regression = [case for case in cases if case.get("suite") == "flow_regression"]
    if limit < len(regression):
        raise ValueError(f"limit must be at least {len(regression)} to preserve flow scenarios")
    selected = list(regression)
    buckets = {}
    for case in cases:
        if case.get("suite") != "flow_regression":
            buckets.setdefault((case["category"], case["questionType"]), []).append(case)
    while len(selected) < limit and buckets:
        for key in list(buckets):
            if len(selected) >= limit:
                break
            selected.append(buckets[key].pop(0))
            if not buckets[key]:
                del buckets[key]
    return copy.deepcopy(selected)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUTPUT)
    args = parser.parse_args()
    load = lambda path: json.loads(path.read_text(encoding="utf-8"))
    cases = build_cases(load(ROOT / "scripts/chat_judge/data/chat_judge_validation_v2.json"),
                        load(ROOT / "scripts/chat_judge/data/chat_judge_pilot.json"),
                        load(judge.DEFAULT_CATALOG))
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(cases, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(cases)} conversations, {sum(len(case['turns']) for case in cases)} real API questions: {args.out}")


if __name__ == "__main__":
    main()
