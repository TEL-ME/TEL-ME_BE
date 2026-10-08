"""별도 평가 DB에서 요청 수, 실제 채팅 답변, 검색 근거와 SSE를 기록한다."""

import argparse
import gzip
import hashlib
import importlib.util
import json
from datetime import datetime, timezone
from pathlib import Path
from types import SimpleNamespace


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", required=True)
    parser.add_argument("--base-url", default="http://localhost:18089")
    parser.add_argument("--postgres-container", default="telme-flow-postgres")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not args.database.startswith("telme_132_"):
        parser.error("별도 telme_132_ 평가 DB를 사용해야 합니다.")

    folder = Path(__file__).resolve().parent
    spec = importlib.util.spec_from_file_location(
        "live", folder.parent / "compound-faq-evaluation/run_live_api.py")
    live = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(live)
    faq = json.loads((folder / "faq-csv-cases.json").read_text(encoding="utf-8"))
    extra = json.loads((folder / "request-objective-cases.json").read_text(encoding="utf-8"))
    cases = [next(row for row in faq if row["id"] == key)
             for key in ["PLAN-0041", "SERVICE-0016", "BILLING-0051", "TERMINATE-0100"]]
    cases.insert(1, {**cases[0], "id": "PLAN-0041-SPACING",
                     "variants": [cases[0]["variants"][0].replace(" ", "")]})
    cases += [row for row in extra if row["id"] in ["OVERVIEW-01", "OVERVIEW-04", "OVERVIEW-05",
                                                   "INDEPENDENT-01", "INDEPENDENT-02", "COMPARISON-02"]]
    repo = folder.parents[1]
    digest = hashlib.sha256()
    for source in sorted((repo / "src/main/java").rglob("*.java")):
        digest.update(source.relative_to(repo).as_posix().encode("utf-8"))
        digest.update(source.read_bytes())
    result = {"recordedAt": datetime.now(timezone.utc).isoformat(), "database": args.database,
              "model": "exaone3.5:7.8b", "sourceTreeSha256": digest.hexdigest(),
              "cases": [], "scope": "요청 수 및 저장, 답변과 SSE 일치 검증. 답변 정확도는 원시 근거와 대조 필요."}
    live_args = SimpleNamespace(base_url=args.base_url, database=args.database,
                                postgres_container=args.postgres_container, scenario="standard")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    for item in cases:
        case = (item["id"], item["variants"][0], item["expectedCount"], None, [])
        try:
            row = live.run_case(live_args, case)
        except Exception as error:
            row = {"id": case[0], "question": case[1], "issues": [f"{type(error).__name__}: {error}"]}
        result["cases"].append(row)
        with args.output.open("wb") as raw:
            with gzip.GzipFile(fileobj=raw, mode="wb", mtime=0) as compressed:
                compressed.write(json.dumps(result, ensure_ascii=False, indent=2).encode("utf-8"))
        print(item["id"], row["issues"] or "PASS", flush=True)
    return 1 if any(row["issues"] for row in result["cases"]) else 0


if __name__ == "__main__":
    raise SystemExit(main())
