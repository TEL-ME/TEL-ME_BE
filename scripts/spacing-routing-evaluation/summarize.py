"""실제 Spring 라우팅 원시 자료를 집계한다. 모델 호출이나 정답 변경은 하지 않는다."""

import argparse
from collections import Counter
import gzip
import hashlib
import json
import statistics
from pathlib import Path


def summarize(path):
    with gzip.open(path, "rt", encoding="utf-8") as source:
        rows = json.load(source)
    groups = {}
    failures = []
    calls = []
    scored = 0
    for row in rows:
        groups.setdefault(row["id"], set()).add(row["outcome"])
        if row.get("expectedCount", -1) >= 0:
            scored += 1
        if row.get("expectedCount", -1) >= 0 and row["outcome"] != f'{row["expectedIntent"]}:{row["expectedCount"]}':
            failures.append({"id": row["id"], "question": row["question"], "outcome": row["outcome"]})
        calls.extend(row.get("calls", [{"modelElapsedMs": row.get("modelElapsedMs", 0)}]))
    return {
        "records": len(rows),
        "scoredRecords": scored,
        "unscoredRecords": len(rows) - scored,
        "correctIntentAndCount": scored - len(failures),
        "outcomes": dict(Counter(row["outcome"] for row in rows)),
        "groupCount": len(groups),
        "inconsistentGroups": [group for group, outcomes in groups.items() if len(outcomes) != 1],
        "modelCalls": len(calls),
        "modelTotalElapsedMs": sum(call["modelElapsedMs"] for call in calls),
        "modelMedianElapsedMs": statistics.median(call["modelElapsedMs"] for call in calls),
        "failures": failures,
        "sha256": hashlib.sha256(Path(path).read_bytes()).hexdigest(),
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("results", nargs="+", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    arguments = parser.parse_args()
    result = {path.stem.removesuffix(".json"): summarize(path) for path in arguments.results}
    arguments.output.parent.mkdir(parents=True, exist_ok=True)
    arguments.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
