"""Compare the three frozen V7 Judge runs with the 302 human-labeled answers."""

import argparse
from collections import Counter, defaultdict
import gzip
import hashlib
import json
from pathlib import Path
import re

from scripts.chat_judge.experiments.v6_live_chat_pipeline import compare_human_review
from scripts.chat_judge.experiments.v6_live_chat_pipeline import evaluate_chat_pipeline as v6
from scripts.chat_judge.experiments.v7_cross_judge.build_v7_capture import V7_DIR


DEFAULT_HUMAN = V7_DIR / "v7-three-model-full-302-human-review-lyj.md"
MODEL_FILES = {
    "qwen": V7_DIR / "v7-qwen-final-raw.json.gz",
    "gpt-oss-120b": V7_DIR / "v7-gpt-oss-120b-raw.json.gz",
    "sonnet-4-6": V7_DIR / "v7-sonnet-4-6-raw.json.gz",
}
OLD_HUMAN_DIR = V7_DIR.parent / "V6-live-chat-pipeline"
ROW = re.compile(
    r"^\|\s*(\d+)\s*\|\s*([^|]+?)\s+\(turn (\d+)\)\s*\|\s*"
    r"([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|"
)
DEFERRED = {None, "REVIEW"}


def read_human(path):
    labels = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = ROW.match(line)
        if not match:
            continue
        number, case_id, turn, grounding, quality, abstention = match.groups()
        key = (case_id, int(turn))
        if key in labels or int(number) != len(labels) + 1:
            raise ValueError(f"중복되거나 순서가 잘못된 사람 판정: {key}")
        labels[key] = {"grounding": grounding.strip(), "quality": quality.strip(),
                       "abstention": abstention.strip()}
    if len(labels) != 302:
        raise ValueError(f"사람 판정이 302건이 아닙니다: {len(labels)}")
    return labels


def read_model(path, alias):
    with gzip.open(path, "rt", encoding="utf-8") as stream:
        document = json.load(stream)
    rows = {(row["caseId"], row["turnIndex"]): row["models"][alias]
            for row in document["turns"] if alias in row.get("models", {})}
    return document, rows


def answer_label(row, axis, single_question):
    if row.get("axisStatus", {}).get(axis) != "SCORED":
        return None
    if axis == "grounding":
        return (row.get("grounding", {}).get("result") or {}).get("overall")
    if axis == "abstention":
        return (row.get("abstentionDecision") or {}).get("label")
    groups = (row.get("quality", {}).get("result") or {}).get("groups") or []
    if not single_question or len(groups) != 1:
        return None
    return groups[0].get("outcome")


def summarize_axis(human, models, keys, axis):
    summary = {"population": len(keys), "models": {}, "consensus": {}, "disagreements": {}}
    labels = {alias: {key: answer_label(rows[key], axis, True) for key in keys}
              for alias, rows in models.items()}
    for alias, predicted in labels.items():
        counts = Counter()
        confusion = defaultdict(Counter)
        errors = defaultdict(list)
        for key in keys:
            actual, value = human[key][axis], predicted[key]
            if value in DEFERRED:
                counts["unresolved"] += 1
                counts["unscored" if value is None else "review"] += 1
                continue
            counts["resolved"] += 1
            counts["match" if value == actual else "mismatch"] += 1
            confusion[actual][value] += 1
            if value != actual:
                errors[f"{actual} -> {value}"].append(key[0])
        summary["models"][alias] = {
            "counts": dict(counts),
            "confusion": {gold: dict(found) for gold, found in confusion.items()},
            "errors": {transition: sorted(ids) for transition, ids in errors.items()},
        }
    consensus = Counter()
    unanimous_wrong = []
    majority_wrong = []
    minority_outcomes = Counter()
    three_way = []
    aliases = tuple(models)
    for key in keys:
        values = {alias: labels[alias][key] for alias in aliases}
        if any(value in DEFERRED for value in values.values()):
            consensus["incomplete"] += 1
            continue
        frequency = Counter(values.values())
        if len(frequency) == 1:
            correct = next(iter(frequency)) == human[key][axis]
            consensus["unanimous_correct" if correct else "unanimous_wrong"] += 1
            if not correct:
                unanimous_wrong.append({"id": key[0], "human": human[key][axis],
                                        "models": values})
        elif max(frequency.values()) == 2:
            majority = frequency.most_common(1)[0][0]
            correct = majority == human[key][axis]
            consensus["majority_correct" if correct else "majority_wrong"] += 1
            if not correct:
                majority_wrong.append({"id": key[0], "human": human[key][axis],
                                       "models": values})
            for alias, value in values.items():
                if value != majority:
                    outcome = ("wrong" if correct else
                               "right" if value == human[key][axis] else "also_wrong")
                    minority_outcomes[f"{alias}_{outcome}"] += 1
        else:
            consensus["three_way"] += 1
            three_way.append({"id": key[0], "human": human[key][axis], "models": values})
    summary["consensus"] = dict(consensus)
    summary["unanimousWrongCases"] = unanimous_wrong
    summary["majorityWrongCases"] = majority_wrong
    summary["threeWayCases"] = three_way
    summary["minorityOutcomes"] = dict(minority_outcomes)
    return summary


def strict_decision(row, axis):
    """Mirror the V7 run_cross_judge consensus fields for a completed axis."""
    if row.get("axisStatus", {}).get(axis) != "SCORED":
        return None
    result = (row.get(axis, {}).get("result") or {})
    if axis == "grounding":
        if not result.get("overall"):
            return None
        return result["overall"], tuple((claim["claim"], claim["verdict"])
                                        for claim in result.get("claims", []))
    if axis == "quality":
        return tuple(group["outcome"] for group in result.get("groups", []))
    label = (row.get("abstentionDecision") or {}).get("label")
    if label is None:
        return None
    return label, tuple(part["evidenceAnswerability"] for part in
                        row.get("abstention", {}).get("questionParts", []))


def summarize_strict_consensus(human, models, single):
    aliases = tuple(models)
    keys = set.intersection(*(set(rows) for rows in models.values()))
    accepted = []
    outside_rejected = []
    for key in sorted(keys):
        reasons = []
        if any(models[alias][key].get("requiresReview") for alias in aliases):
            reasons.append("model_requires_review")
        for axis in ("grounding", "quality", "abstention"):
            values = [strict_decision(models[alias][key], axis) for alias in aliases]
            if any(value is None for value in values):
                reasons.append(axis + "_unscored")
            elif len(set(values)) != 1:
                reasons.append(axis + "_disagree")
        if reasons:
            if key not in human:
                outside_rejected.append({"id": key[0], "reasons": reasons})
            continue
        accepted.append(key)
    single_set = set(single)
    human_accepted = [key for key in accepted if key in human]
    single_accepted = [key for key in human_accepted if key in single_set]
    wrong = []
    for key in single_accepted:
        mismatches = [axis for axis in ("grounding", "quality", "abstention")
                      if answer_label(models[aliases[0]][key], axis, True) != human[key][axis]]
        if mismatches:
            wrong.append({"id": key[0], "axes": mismatches})
    return {"completedAnswerTurns": len(keys), "acceptedTotal": len(accepted),
            "acceptedInHuman302": len(human_accepted),
            "acceptedOutsideHuman302": len(accepted) - len(human_accepted),
            "acceptedHumanSingleQuestion": len(single_accepted),
            "humanSingleQuestionWrong": wrong,
            "rejectedOutsideHuman302": outside_rejected}


def historical_label_conflicts(human):
    key_file = OLD_HUMAN_DIR / "20261005-heldout40-human-review-key.json"
    human_file = OLD_HUMAN_DIR / "20261005-heldout40-human-review-lyj.md"
    old, old_sha = compare_human_review.load_human_review(
        human_file, v6.load_json(key_file))
    overlap = 0
    differences = []
    for review_id, previous in old.items():
        key = previous["caseId"], previous["turnIndex"]
        if key not in human:
            continue
        overlap += 1
        changed = {}
        for axis in ("grounding", "abstention"):
            if previous[axis] != human[key][axis]:
                changed[axis] = {"old": previous[axis], "human302": human[key][axis]}
        if len(previous["quality"]) == 1 and previous["quality"][0] != human[key]["quality"]:
            changed["quality"] = {"old": previous["quality"][0],
                                  "human302": human[key]["quality"]}
        if changed:
            differences.append({"reviewId": review_id, "caseId": key[0],
                                "changes": changed})
    return {"oldFile": human_file.name, "oldSha256": old_sha,
            "overlap": overlap, "changedCases": differences}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--human", type=Path, default=DEFAULT_HUMAN)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    human = read_human(args.human)
    documents, models = {}, {}
    for alias, path in MODEL_FILES.items():
        documents[alias], models[alias] = read_model(path, alias)
        if set(human) - set(models[alias]):
            raise ValueError(f"{alias} 결과에 사람 판정 ID가 없습니다.")
    hashes = {alias: {name: documents[alias].get("identity", {}).get(name) for name in
                      ("captureSha256", "catalogSha256", "judgeCodeSha256")}
              for alias in documents}
    if any(not value for fields in hashes.values() for value in fields.values()):
        raise ValueError("원시 결과의 캡처, FAQ 목록 또는 Judge 코드 해시가 없습니다.")
    if len({json.dumps(value, sort_keys=True) for value in hashes.values()}) != 1:
        raise ValueError("세 모델이 동일한 입력 및 Judge 코드로 실행되지 않았습니다.")
    for key in human:
        signatures = {json.dumps((models[alias][key]["question"],
                                  models[alias][key]["answer"],
                                  models[alias][key]["sources"]),
                                 ensure_ascii=False, sort_keys=True)
                      for alias in models}
        if len(signatures) != 1:
            raise ValueError(f"세 모델의 질문, 답변, 검색 근거가 다릅니다: {key}")
    single = [key for key in human if len((models["sonnet-4-6"][key].get("quality", {})
                   .get("result") or {}).get("groups") or []) == 1]
    compound = [key for key in human if key not in single]
    result = {
        "scope": {"humanFile": args.human.name,
                  "humanSha256": hashlib.sha256(args.human.read_bytes()).hexdigest(),
                  "humanReviewed": len(human), "singleQuestion": len(single),
                  "multipleQuestion": len(compound), "humanCounts": {
                      axis: dict(Counter(row[axis] for row in human.values()))
                      for axis in ("grounding", "quality", "abstention")}},
        "inputs": {alias: {"file": MODEL_FILES[alias].name,
                           "sha256": hashlib.sha256(MODEL_FILES[alias].read_bytes()).hexdigest(),
                           "recordedHashes": hashes[alias]}
                   for alias in models},
        "grounding": summarize_axis(human, models, list(human), "grounding"),
        "qualitySingleQuestion": summarize_axis(human, models, single, "quality"),
        "abstention": summarize_axis(human, models, list(human), "abstention"),
        "multipleQuestion": {"ids": [key[0] for key in compound],
                             "humanCounts": dict(Counter(human[key]["quality"] for key in compound))},
        "strictConsensus": summarize_strict_consensus(human, models, single),
        "historicalHumanReview": historical_label_conflicts(human),
    }
    serialized = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.out:
        args.out.write_text(serialized, encoding="utf-8")
    else:
        print(serialized)


if __name__ == "__main__":
    main()
