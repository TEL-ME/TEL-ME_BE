#!/usr/bin/env python3
"""Build conservative, query-scoped FAQ alternative groups from two-pass review."""

import hashlib
import json
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "scripts/data/faq_full_1150.json"
DATASET = ROOT / "scripts/chat_judge/data/chat_judge_validation_v2.json"
CANDIDATES = ROOT / "scripts/data/faq_equivalence_review_candidates_v2.json"
FIRST_PASS = ROOT / "scripts/data/faq_equivalence_adjudication_v2.json"
STRICT_PASS = ROOT / "scripts/data/faq_equivalence_strict_review_v1.json"
OUTPUT = ROOT / "scripts/data/faq_equivalence_labels_v2.json"


def digest(value):
    raw = json.dumps(value, ensure_ascii=False, sort_keys=True,
                     separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(raw).hexdigest()


def load(path):
    return json.loads(path.read_text(encoding="utf-8"))


def selected_ids(dataset):
    return {
        source_id
        for case in dataset["cases"]
        for source_id in case["adequacyInput"].get("goldSourceSlotIds", [])
    }


def build(catalog, dataset, candidate_file, first, strict):
    by_id = {item["slot_id"]: item for item in catalog}
    selected = selected_ids(dataset)
    if not selected <= by_id.keys():
        raise ValueError(f"Unknown selected FAQ IDs: {sorted(selected - by_id.keys())}")

    strict_pairs = {
        key for key, result in strict["decisions"].items()
        if result["status"] == "SCORED" and result["equivalent"] is True
    }
    first_pairs = {
        key for key, result in first["decisions"].items()
        if result["status"] == "SCORED" and result["equivalent"] is True
    }
    consensus = strict_pairs & first_pairs
    first_decisions = first["decisions"]
    strict_decisions = strict["decisions"]
    candidate_by_pair = {
        f"{row['primaryId']}|{row['candidateId']}": row
        for row in candidate_file["candidates"]
    }

    evidence_by_primary = defaultdict(list)
    for key in sorted(consensus):
        primary_id, candidate_id = key.split("|", 1)
        if primary_id not in selected:
            continue
        candidate = candidate_by_pair.get(key)
        if not candidate:
            raise ValueError(f"Consensus decision missing candidate evidence: {key}")
        if candidate["category"] != by_id[primary_id]["category"] \
                or candidate["category"] != by_id[candidate_id]["category"]:
            raise ValueError(f"Cross-category alternative is not allowed: {key}")
        evidence_by_primary[primary_id].append({
            "type": "BGE_CANDIDATE_TWICE_ADJUDICATED",
            "candidateId": candidate_id,
            "questionCosine": next((item.get("questionCosine") for item in candidate["evidence"]
                                     if item.get("questionCosine") is not None), None),
            "answerCosine": next((item.get("answerCosine") for item in candidate["evidence"]
                                   if item.get("answerCosine") is not None), None),
            "candidateEvidence": candidate["evidence"],
            "firstPassReason": first_decisions[key]["reason"],
            "strictPassReason": strict_decisions[key]["reason"],
        })

    mapping = {}
    for primary_id in sorted(selected):
        accepted = sorted({primary_id} | {
            item["candidateId"] for item in evidence_by_primary.get(primary_id, [])
        })
        mapping[primary_id] = {
            "acceptedSourceIds": accepted,
            "evidence": evidence_by_primary.get(primary_id, []),
        }

    final_edges = sum(len(item["acceptedSourceIds"]) - 1 for item in mapping.values())
    result = {
        "schemaVersion": 2,
        "sourceCatalog": CATALOG.name,
        "sourceCatalogCount": len(catalog),
        "sourceCatalogSha256": digest(catalog),
        "controlledDataset": DATASET.name,
        "controlledPrimarySourceIdsSha256": digest(sorted(selected)),
        "candidateSource": CANDIDATES.name,
        "candidateSourceSha256": digest(candidate_file),
        "firstPassSource": FIRST_PASS.name,
        "firstPassSha256": digest(first),
        "strictPassSource": STRICT_PASS.name,
        "strictPassSha256": digest(strict),
        "candidateScreen": {
            "embeddingModel": candidate_file["embeddingModel"],
            "sameCategoryOnly": True,
            "questionCosineMinimum": candidate_file["questionCosineThreshold"],
            "answerCosineMinimum": candidate_file["answerCosineThreshold"],
            "candidatePairCount": candidate_file["candidateCount"],
        },
        "adjudication": {
            "model": first["model"],
            "passes": 2,
            "strictPassPolicy": strict["policy"],
            "acceptedOnlyWhenBothPassesAgree": True,
            "humanVerified": False,
        },
        "labelPolicy": "For each selected primary FAQ query, accept same-category alternatives only when both EXAONE passes independently judge the candidate FAQ question and answer to preserve the query scope and all materially required facts and conditions.",
        "completeness": "SCREENED_CANDIDATES_ONLY; candidate recall is limited by the BGE-M3 question cosine >= 0.70 and answer cosine >= 0.90 screen plus existing multi-gold evidence; not an exhaustive equivalence proof over all catalog FAQs.",
        "summary": {
            "selectedPrimaryCount": len(selected),
            "primariesWithAlternatives": sum(len(item["acceptedSourceIds"]) > 1
                                              for item in mapping.values()),
            "acceptedAlternativeEdges": final_edges,
            "candidateCount": candidate_file["candidateCount"],
            "firstPassPositiveCount": len(first_pairs),
            "strictPassPositiveCount": len(strict_pairs),
            "twoPassConsensusCount": len(consensus),
            "consensusForSelectedPrimaries": final_edges,
            "unscoredFirstPass": first["summary"]["unscoredCount"],
            "unscoredStrictPass": strict["summary"]["unscoredCount"],
        },
        "byPrimarySourceId": mapping,
    }
    return result


def main():
    catalog = load(CATALOG)
    dataset = load(DATASET)
    candidate_file = load(CANDIDATES)
    first = load(FIRST_PASS)
    strict = load(STRICT_PASS)
    result = build(catalog, dataset, candidate_file, first, strict)
    OUTPUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n",
                      encoding="utf-8")
    print(json.dumps(result["summary"], ensure_ascii=False))
    print(f"Wrote {OUTPUT}")


if __name__ == "__main__":
    main()
