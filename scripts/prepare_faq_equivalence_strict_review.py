#!/usr/bin/env python3
"""Select first-pass positive candidate pairs for stricter second-pass review."""

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
INPUT_CANDIDATES = ROOT / "scripts/data/faq_equivalence_review_candidates_v2.json"
FIRST_PASS = ROOT / "scripts/data/faq_equivalence_adjudication_v2.json"
OUTPUT = ROOT / "scripts/data/faq_equivalence_positive_review_v1.json"


def digest(value):
    raw = json.dumps(value, ensure_ascii=False, sort_keys=True,
                     separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def main():
    candidates = json.loads(INPUT_CANDIDATES.read_text(encoding="utf-8"))
    first = json.loads(FIRST_PASS.read_text(encoding="utf-8"))
    positive = {
        key for key, value in first["decisions"].items()
        if value["status"] == "SCORED" and value["equivalent"] is True
    }
    result = dict(candidates)
    result["candidates"] = [
        row for row in candidates["candidates"]
        if f"{row['primaryId']}|{row['candidateId']}" in positive
    ]
    result["candidateCount"] = len(result["candidates"])
    result["screeningSource"] = FIRST_PASS.name
    result["screeningSourceSha256"] = digest(first)
    result["acceptedAsGold"] = False
    OUTPUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n",
                      encoding="utf-8")
    print(f"{result['candidateCount']} first-pass positives for strict review: {OUTPUT}")


if __name__ == "__main__":
    main()
