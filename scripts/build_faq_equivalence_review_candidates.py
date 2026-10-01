#!/usr/bin/env python3
"""Build a review set of possible equivalent FAQ pairs for the Judge gold labels."""

import argparse
import hashlib
import json
import urllib.request
from collections import defaultdict
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "scripts/data/faq_full_1150.json"
DATASET = ROOT / "scripts/data/chat_judge_validation_v2.json"
MULTIGOLD_LABELS = ROOT / "scripts/data/faq_equivalence_labels_v2_candidate.json"
OUTPUT = ROOT / "scripts/data/faq_equivalence_review_candidates_v1.json"
MODEL = "bge-m3:latest"
QUESTION_THRESHOLD = 0.75
ANSWER_THRESHOLD = 0.90


def digest(value):
    payload = json.dumps(value, ensure_ascii=False, sort_keys=True,
                         separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def embed(url, texts):
    vectors = []
    for offset in range(0, len(texts), 32):
        payload = json.dumps({"model": MODEL, "input": texts[offset:offset + 32]}).encode("utf-8")
        request = urllib.request.Request(
            url.rstrip("/") + "/api/embed", data=payload,
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(request, timeout=180) as response:
            vectors.extend(json.load(response)["embeddings"])
    matrix = np.asarray(vectors, dtype=np.float32)
    matrix /= np.linalg.norm(matrix, axis=1)[:, None]
    return matrix


def selected_ids(dataset):
    return {
        source_id
        for case in dataset["cases"]
        for source_id in case["adequacyInput"].get("goldSourceSlotIds", [])
    }


def build(catalog, dataset, multigold_labels, url,
          question_threshold=QUESTION_THRESHOLD, answer_threshold=ANSWER_THRESHOLD):
    by_id = {faq["slot_id"]: faq for faq in catalog}
    selected = selected_ids(dataset)
    if not selected <= by_id.keys():
        raise ValueError(f"Unknown selected FAQ IDs: {sorted(selected - by_id.keys())}")
    catalog_index = {faq["slot_id"]: index for index, faq in enumerate(catalog)}

    evidence = defaultdict(list)
    multigold = multigold_labels["byPrimarySourceId"]
    for primary_id in selected:
        for item in multigold.get(primary_id, {}).get("evidence", []):
            for candidate_id in item.get("sourceIds", []):
                if candidate_id != primary_id and candidate_id in by_id:
                    evidence[(primary_id, candidate_id)].append({
                        "type": item["type"],
                        "evalId": item.get("evalId"),
                        "queryCosine": item.get("queryCosine"),
                    })
        for candidate_id in multigold.get(primary_id, {}).get("acceptedSourceIds", []):
            if candidate_id != primary_id and candidate_id in by_id:
                evidence[(primary_id, candidate_id)].append({"type": "EXISTING_ACCEPTED_LABEL"})

    matrix = embed(url, [text for faq in catalog for text in (faq["question"], faq["answer"])])
    q_vectors = matrix[0::2]
    a_vectors = matrix[1::2]
    for primary_id in selected:
        primary = by_id[primary_id]
        i = catalog_index[primary_id]
        same_category = [j for j, faq in enumerate(catalog)
                         if faq["category"] == primary["category"] and j != i]
        if not same_category:
            continue
        indices = np.asarray(same_category, dtype=np.int32)
        q_scores = q_vectors[indices] @ q_vectors[i]
        a_scores = a_vectors[indices] @ a_vectors[i]
        for position, candidate_index in enumerate(indices):
            if q_scores[position] < question_threshold or a_scores[position] < answer_threshold:
                continue
            candidate_id = catalog[candidate_index]["slot_id"]
            evidence[(primary_id, candidate_id)].append({
                "type": "BGE_QUESTION_AND_ANSWER_CANDIDATE",
                "questionCosine": round(float(q_scores[position]), 6),
                "answerCosine": round(float(a_scores[position]), 6),
            })

    candidates = []
    for (primary_id, candidate_id), sources in sorted(evidence.items()):
        primary = by_id[primary_id]
        candidate = by_id[candidate_id]
        candidates.append({
            "primaryId": primary_id,
            "candidateId": candidate_id,
            "category": primary["category"],
            "primary": {"question": primary["question"], "answer": primary["answer"]},
            "candidate": {"question": candidate["question"], "answer": candidate["answer"]},
            "evidence": sources,
        })
    return {
        "schemaVersion": 1,
        "sourceCatalog": CATALOG.name,
        "sourceCatalogSha256": digest(catalog),
        "controlledDataset": DATASET.name,
        "controlledPrimarySourceIdsSha256": digest(sorted(selected)),
        "multigoldLabelSource": MULTIGOLD_LABELS.name,
        "multigoldLabelSha256": digest(multigold_labels),
        "embeddingModel": MODEL,
        "questionCosineThreshold": question_threshold,
        "answerCosineThreshold": answer_threshold,
        "candidateCount": len(candidates),
        "acceptedAsGold": False,
        "candidates": candidates,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ollama-url", default="http://localhost:11434")
    parser.add_argument("--question-threshold", type=float, default=QUESTION_THRESHOLD)
    parser.add_argument("--answer-threshold", type=float, default=ANSWER_THRESHOLD)
    parser.add_argument("--out", type=Path, default=OUTPUT)
    args = parser.parse_args()
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    dataset = json.loads(DATASET.read_text(encoding="utf-8"))
    multigold = json.loads(MULTIGOLD_LABELS.read_text(encoding="utf-8"))
    result = build(catalog, dataset, multigold, args.ollama_url,
                   args.question_threshold, args.answer_threshold)
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{result['candidateCount']} candidates; none added to gold labels yet: {args.out}")


if __name__ == "__main__":
    main()
