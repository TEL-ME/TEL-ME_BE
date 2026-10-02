#!/usr/bin/env python3
"""Create high-confidence, query-scoped alternatives from existing multi-gold labels."""

import argparse
import hashlib
import json
import urllib.request
from collections import defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "scripts/data/faq_full_1150.json"
MULTIGOLD = ROOT / "scripts/data/eval_questions_180_multigold.json"
JUDGE_SET = ROOT / "scripts/chat_judge/data/chat_judge_validation_v2.json"
OUTPUT = ROOT / "scripts/data/faq_equivalence_labels_v1.json"
MODEL = "bge-m3:latest"
QUERY_SIMILARITY_THRESHOLD = 0.90


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
    return vectors


def cosine(left, right):
    dot = sum(a * b for a, b in zip(left, right))
    left_norm = sum(value * value for value in left) ** 0.5
    right_norm = sum(value * value for value in right) ** 0.5
    return dot / (left_norm * right_norm)


def selected_source_ids(dataset):
    return {
        source_id
        for case in dataset["cases"]
        for source_id in case["adequacyInput"].get("goldSourceSlotIds", [])
    }


def digest(value):
    payload = json.dumps(value, ensure_ascii=False, sort_keys=True,
                         separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def build(catalog, multigold, dataset, url, threshold=QUERY_SIMILARITY_THRESHOLD):
    by_id = {faq["slot_id"]: faq for faq in catalog}
    selected = selected_source_ids(dataset)
    unknown = selected - by_id.keys()
    if unknown:
        raise ValueError(f"FAQ catalog is missing IDs: {sorted(unknown)}")

    exact_answers = defaultdict(set)
    for faq in catalog:
        exact_answers[(faq["category"], faq["answer"].strip())].add(faq["slot_id"])

    eligible_rows = []
    pairs = []
    for faq_id in sorted(selected):
        faq = by_id[faq_id]
        for row in multigold:
            row_ids = row.get("expected_slot_id") or []
            if faq_id not in row_ids:
                continue
            if not any(source_id in by_id and by_id[source_id]["category"] == faq["category"]
                       for source_id in row_ids):
                continue
            eligible_rows.append((faq_id, row))
            pairs.append((faq["question"], row["question"]))

    vectors = embed(url, [text for pair in pairs for text in pair]) if pairs else []
    alternatives = {}
    for faq_id in sorted(selected):
        faq = by_id[faq_id]
        accepted = {faq_id}
        evidence = []
        exact_ids = sorted(exact_answers[(faq["category"], faq["answer"].strip())] - {faq_id})
        accepted.update(exact_ids)
        if exact_ids:
            evidence.append({"type": "EXACT_ANSWER_SAME_CATEGORY", "sourceIds": exact_ids})
        alternatives[faq_id] = {"acceptedSourceIds": sorted(accepted), "evidence": evidence}

    for pair_index, (faq_id, row) in enumerate(eligible_rows):
        faq = by_id[faq_id]
        score = cosine(vectors[pair_index * 2], vectors[pair_index * 2 + 1])
        if score < threshold:
            continue
        row_ids = sorted({source_id for source_id in (row.get("expected_slot_id") or [])
                          if source_id in by_id and by_id[source_id]["category"] == faq["category"]})
        target = alternatives[faq_id]
        target["acceptedSourceIds"] = sorted(set(target["acceptedSourceIds"]) | set(row_ids))
        target["evidence"].append({
            "type": "MULTIGOLD_QUERY_MATCH",
            "evalId": row["eval_id"],
            "query": row["question"],
            "queryCosine": round(score, 6),
            "sourceIds": row_ids,
        })

    for faq_id in selected:
        target = alternatives.setdefault(faq_id, {"acceptedSourceIds": [faq_id], "evidence": []})
        target["acceptedSourceIds"] = sorted(set(target["acceptedSourceIds"]))

    return {
        "schemaVersion": 1,
        "sourceCatalog": CATALOG.name,
        "sourceCatalogCount": len(catalog),
        "sourceCatalogSha256": digest(catalog),
        "multigoldSource": MULTIGOLD.name,
        "multigoldRowCount": len(multigold),
        "multigoldSha256": digest(multigold),
        "controlledDataset": JUDGE_SET.name,
        "controlledPrimarySourceIdsSha256": digest(sorted(selected)),
        "embeddingModel": MODEL,
        "queryCosineThreshold": threshold,
        "labelPolicy": "same-category exact answer matches plus query-scoped existing multi-gold labels whose FAQ question similarity meets the threshold",
        "completeness": "KNOWN_ALTERNATIVES_ONLY; not a proof that every catalog equivalent was found",
        "byPrimarySourceId": alternatives,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ollama-url", default="http://localhost:11434")
    parser.add_argument("--threshold", type=float, default=QUERY_SIMILARITY_THRESHOLD)
    parser.add_argument("--out", type=Path, default=OUTPUT)
    args = parser.parse_args()
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    multigold = json.loads(MULTIGOLD.read_text(encoding="utf-8"))
    dataset = json.loads(JUDGE_SET.read_text(encoding="utf-8"))
    result = build(catalog, multigold, dataset, args.ollama_url, args.threshold)
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    groups = result["byPrimarySourceId"]
    expanded = sum(len(item["acceptedSourceIds"]) > 1 for item in groups.values())
    print(f"{len(groups)} primary IDs; {expanded} have known alternatives; wrote {args.out}")


if __name__ == "__main__":
    main()
