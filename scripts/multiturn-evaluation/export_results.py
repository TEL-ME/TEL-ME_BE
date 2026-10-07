"""Spring 실측 원시 자료를 보존하고 문구 보존 지표와 실행 정보를 집계한다."""
import argparse
import collections
import gzip
import hashlib
import json
from pathlib import Path
import statistics
import subprocess
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent


def read_rows(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def archive(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    data = source.read_bytes()
    target.write_bytes(gzip.compress(data, mtime=0))
    return {"source": str(source.relative_to(ROOT)), "archive": str(target.relative_to(ROOT)),
            "uncompressedSha256": hashlib.sha256(data).hexdigest(), "bytes": len(data)}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--summary", required=True, type=Path)
    parser.add_argument("--api", required=True, type=Path)
    parser.add_argument("--boundaries", required=True, type=Path)
    parser.add_argument("--checks", required=True, type=Path)
    args = parser.parse_args()
    for key, value in vars(args).items():
        setattr(args, key, value.resolve())
    results = HERE / "results"
    summaries = read_rows(args.summary)
    aggregates = {}
    for mode in ("BASELINE_TEXT", "IMPROVED_TEXT", "GROUNDED_MEMORY"):
        rows = [row for row in summaries if row["mode"] == mode]
        aggregates[mode] = {
            "cases": len(rows), "accepted": sum(row["validation"] == "ACCEPTED" for row in rows),
            "allLiteralAnchorsPreserved": sum(bool(row.get("literalAnchorChecks"))
                                              and all(row["literalAnchorChecks"].values()) for row in rows),
            "medianCaseMillis": statistics.median(row["elapsedMillis"] for row in rows),
            "medianEstimatedMemoryTokens": statistics.median(row.get("estimatedTokens", 0) for row in rows),
            "modelCalls": sum(len(row["requests"]) for row in rows),
            "categories": dict(collections.Counter(row["case"]["category"] for row in rows)),
        }
    aggregates["notes"] = ["문구 보존 집계이며 의미 정확도나 환각률이 아님",
                           "60건은 6개 대화 유형의 변형 및 반복 입력이며 독립 사용자 표본이 아님",
                           "분할 양쪽에 같은 유형이 있으므로 독립 블라인드 검증 결과가 아님"]
    aggregates["distinctInputDialogues"] = len({json.dumps(row["case"]["messages"], sort_keys=True,
                                                         ensure_ascii=False) for row in summaries})
    write_json(results / "V1-summary-comparison" / "metrics.json", aggregates)
    final_model = args.api.parent / args.api.name.replace("cases-", "model-", 1)
    selected_runs = (
        (args.summary, "V1-summary-comparison", "final-summary.jsonl.gz"),
        (args.boundaries, "V2-live-api", "final-boundaries.jsonl.gz"),
        (args.api, "V2-live-api", "final-cases.jsonl.gz"),
        (final_model, "V2-live-api", "final-model.jsonl.gz"),
    )
    archives = []
    for source, experiment, name in selected_runs:
        if not source.is_file():
            raise FileNotFoundError(source)
        entry = archive(source, results / experiment / "raw" / name)
        entry["isFinal"] = True
        archives.append(entry)
    checks = args.checks.read_text(encoding="utf-8-sig", errors="strict")
    if "BUILD SUCCESSFUL" not in checks or "BUILD FAILED" in checks:
        raise ValueError("완료된 빌드 성공 로그가 필요합니다.")
    import xml.etree.ElementTree as ET
    totals = collections.Counter()
    for path in (ROOT / "build" / "test-results" / "test").glob("TEST-*.xml"):
        report = ET.parse(path).getroot()
        for key in ("tests", "failures", "errors", "skipped"):
            totals[key] += int(report.get(key, 0))
    if totals["failures"] or totals["errors"]:
        raise ValueError("실패한 테스트 결과는 최종 검증으로 내보낼 수 없습니다.")
    write_json(results / "verification.json", dict(totals))
    tags = json.load(urllib.request.urlopen("http://localhost:11434/api/tags", timeout=10))
    changed = subprocess.check_output(["git", "diff", "--name-only"], cwd=ROOT, encoding="utf-8").splitlines()
    changed += subprocess.check_output(["git", "ls-files", "--others", "--exclude-standard"],
                                       cwd=ROOT, encoding="utf-8").splitlines()
    hashes = {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest()
              for name in changed if name.endswith(".java") and (ROOT / name).exists()}
    write_json(results / "manifest.json", {
        "baseCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, encoding="utf-8").strip(),
        "changesNotCommitted": True, "codeFileSha256": hashes,
        "models": [{"name": model["name"], "digest": model["digest"], "details": model.get("details")}
                   for model in tags["models"]], "archives": archives,
        "intermediateRunsOmitted": (
            len(list((ROOT / ".measure" / "telme121" / "summary-comparison").glob("raw-*.jsonl")))
            + len(list((ROOT / ".measure" / "telme121" / "live-api").glob("*.jsonl")))
            - len(selected_runs)
        ),
        "selectedInputs": {key: str(value) for key, value in vars(args).items()},
        "faqSnapshot": json.loads((ROOT / ".measure" / "telme121" / "faq-snapshot" / "manifest.json")
                                   .read_text(encoding="utf-8")),
        "liveApiRows": len(read_rows(args.api)), "boundaryRows": len(read_rows(args.boundaries)),
        "buildCommand": "gradlew.bat clean test build --build-cache",
    })


if __name__ == "__main__":
    main()
