"""Replay the 153 labeled routing cases through the live chat API."""

import argparse
import gzip
import hashlib
import importlib.util
import json
import time
from datetime import datetime, timezone
from pathlib import Path
from types import SimpleNamespace


def save(path, data):
    temporary = path.with_suffix(path.suffix + ".tmp")
    with temporary.open("wb") as raw:
        with gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as compressed:
            compressed.write(json.dumps(data, ensure_ascii=False).encode("utf-8"))
    temporary.replace(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", required=True)
    parser.add_argument("--base-url", default="http://localhost:18089")
    parser.add_argument("--postgres-container", default="telme-flow-postgres")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not args.database.startswith("telme_132_"):
        parser.error("an isolated telme_132_ database is required")

    folder = Path(__file__).resolve().parent
    routing_path = folder / "runs/request-objective-routing.json.gz"
    with gzip.open(routing_path, "rt", encoding="utf-8") as raw:
        routing = json.load(raw)
    selected = [(index, case) for index, case in enumerate(routing)
                if index < 60 or (60 <= index < 183 and case["expectedCount"] >= 0)]
    assert len(selected) == 153

    spec = importlib.util.spec_from_file_location(
        "live", folder.parent / "compound-faq-evaluation/run_live_api.py")
    live = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(live)
    source_hash = hashlib.sha256()
    repo = folder.parents[1]
    for source in sorted((repo / "src/main/java").rglob("*.java")):
        source_hash.update(source.relative_to(repo).as_posix().encode("utf-8"))
        source_hash.update(source.read_bytes())

    args.output.parent.mkdir(parents=True, exist_ok=True)
    if args.output.exists():
        with gzip.open(args.output, "rt", encoding="utf-8") as raw:
            result = json.load(raw)
        assert result["sourceTreeSha256"] == source_hash.hexdigest()
        assert result["database"] == args.database
    else:
        result = {
            "recordedAt": datetime.now(timezone.utc).isoformat(),
            "sourceTreeSha256": source_hash.hexdigest(),
            "routingSourceSha256": hashlib.sha256(routing_path.read_bytes()).hexdigest(),
            "database": args.database,
            "model": "exaone3.5:7.8b",
            "selection": "first 60 regression cases and 93 labeled FAQ cases from routing result",
            "cases": [],
        }
    completed = {row["routingIndex"] for row in result["cases"]}
    live_args = SimpleNamespace(base_url=args.base_url, database=args.database,
                                postgres_container=args.postgres_container,
                                scenario="standard")
    started = time.monotonic()
    for sequence, (index, item) in enumerate(selected, 1):
        if index in completed:
            continue
        case = (item["id"], item["question"], item["expectedCount"], None, [])
        begin = time.monotonic()
        try:
            row = live.run_case(live_args, case)
        except Exception as error:
            row = {"id": item["id"], "question": item["question"],
                   "issues": [f"{type(error).__name__}: {error}"]}
        row["routingIndex"] = index
        row["sequence"] = sequence
        row["expectedCount"] = item["expectedCount"]
        row["expectedIntent"] = item["expectedIntent"]
        row["routingAttempt"] = item["attempt"]
        row["elapsedSeconds"] = round(time.monotonic() - begin, 3)
        result["cases"].append(row)
        result["elapsedSeconds"] = round(time.monotonic() - started, 3)
        save(args.output, result)
        print(f"{sequence:03d}/153 {item['id']} {row['elapsedSeconds']}s "
              f"{row['issues'] or 'PASS'}", flush=True)
    return 1 if any(row["issues"] for row in result["cases"]) else 0


if __name__ == "__main__":
    raise SystemExit(main())
