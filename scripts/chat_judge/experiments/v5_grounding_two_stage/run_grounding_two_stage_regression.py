#!/usr/bin/env python3
"""Re-run the archived 35 unscored grounding cases using the two-stage judge."""

import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import gzip
import json
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

from scripts.chat_judge import judge_chat_flow as judge


ROOT = Path(__file__).resolve().parents[4]
DEFAULT_SOURCE = ROOT / "docs/chat-judge/experiments/V4-split-judge-telme100/20261001-validation-v2-telme100-improved-raw.json.gz"
DEFAULT_TARGET = ROOT / "docs/chat-judge/experiments/V5-grounding-two-stage/20261002-grounding-retry-vllm-raw.json.gz"
DEFAULT_OUTPUT = ROOT / "docs/chat-judge/experiments/V5-grounding-two-stage/20261002-grounding-two-stage-vllm-retry-35-raw.json.gz"


def save_checkpoint(path, result):
    temporary = path.with_suffix(path.suffix + ".tmp")
    with gzip.open(temporary, "wt", encoding="utf-8", newline="") as stream:
        json.dump(result, stream, ensure_ascii=False, indent=2)
        stream.write("\n")
    temporary.replace(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE)
    parser.add_argument("--target", type=Path, default=DEFAULT_TARGET)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--url", default="http://localhost:8001")
    parser.add_argument("--model", default="qwen3-14b-awq")
    parser.add_argument("--timeout", type=int, default=240)
    parser.add_argument("--workers", type=int, default=8)
    args = parser.parse_args()

    with gzip.open(args.source, "rt", encoding="utf-8") as stream:
        source = json.load(stream)
    with gzip.open(args.target, "rt", encoding="utf-8") as stream:
        target = json.load(stream)
    unscored_ids = list(target["summary"]["remainingErrors"])
    by_id = {case["caseId"]: case for case in source["cases"]}
    if len(unscored_ids) != 35 or any(case_id not in by_id for case_id in unscored_ids):
        raise ValueError("The source must identify all 35 archived unscored cases.")

    request = urllib.request.Request(args.url.rstrip("/") + "/v1/models")
    with urllib.request.urlopen(request, timeout=15) as response:
        models = json.load(response).get("data", [])
    if args.model not in {model["id"] for model in models}:
        raise ValueError(f"vLLM does not serve requested model {args.model!r}; available: {models}")

    output = {
        "schemaVersion": 1,
        "kind": "grounding-two-stage-retry-regression",
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "sourceFile": args.source.name,
        "sourceSha256": judge.sha256(args.source.read_bytes()),
        "targetFile": args.target.name,
        "targetSha256": judge.sha256(args.target.read_bytes()),
        "judgeScriptSha256": judge.sha256(Path(judge.__file__).read_bytes()),
        "claimExtractionRubricSha256": judge.sha256(judge.CLAIM_EXTRACTION_RUBRIC.encode("utf-8")),
        "claimExtractionSchemaSha256": judge.sha256(judge.CLAIM_EXTRACTION_SCHEMA),
        "groundingRubricSha256": judge.sha256(judge.GROUNDING_RUBRIC.encode("utf-8")),
        "groundingSchemaSha256": judge.sha256(judge.GROUNDING_SCHEMA),
        "backend": "vLLM OpenAI-compatible API",
        "vllmVersion": "0.29.0",
        "model": args.model,
        "modelQuantization": "AWQ 4-bit",
        "maxModelLen": 4096,
        "sampling": {"temperature": 0, "topP": 0.95, "topK": 20, "maxTokens": 2048,
                      "thinking": False},
        "wslUvaPatch": "test/vllm_patch/buffer_utils.py",
        "wslUvaPatchSha256": judge.sha256(
            (ROOT / "test/vllm_patch/buffer_utils.py").read_bytes()
        ),
        "servedModels": models,
        "workers": args.workers,
        "cases": [],
    }
    args.out.parent.mkdir(parents=True, exist_ok=True)

    def score(case_id):
        case = by_id[case_id]
        input_data = case.get("groundingInput")
        if not isinstance(input_data, dict):
            raise ValueError(f"Archived input missing for {case_id}.")
        item = {"caseId": case_id, "input": input_data}
        try:
            record = judge.vllm_chat(args.url, args.model, "grounding", input_data, timeout=args.timeout)
            item.update({"status": "SCORED", "record": record})
        except judge.JudgeCallError as error:
            item.update({"status": "UNSCORED", "record": error.record})
        return item

    with ThreadPoolExecutor(max_workers=args.workers) as pool:
        futures = {pool.submit(score, case_id): case_id for case_id in unscored_ids}
        for future in as_completed(futures):
            item = future.result()
            output["cases"].append(item)
            output["cases"].sort(key=lambda row: unscored_ids.index(row["caseId"]))
            case_id = item["caseId"]
            output["summary"] = {
                "total": len(output["cases"]),
                "scored": sum(row["status"] == "SCORED" for row in output["cases"]),
                "unscored": sum(row["status"] == "UNSCORED" for row in output["cases"]),
                "claimCount": sum(len(row.get("record", {}).get("result", {}).get("claims", []))
                                  for row in output["cases"]),
                "totalDurationMs": sum(row.get("record", {}).get("durationMs", 0)
                                       for row in output["cases"]),
            }
            save_checkpoint(args.out, output)
            print(f"{item['status']} {case_id} ({len(output['cases'])}/{len(unscored_ids)})", flush=True)

    print(json.dumps(output["summary"], ensure_ascii=False, indent=2))
    print(args.out)


if __name__ == "__main__":
    main()
