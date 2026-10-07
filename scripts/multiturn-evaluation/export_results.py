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


def check_build(path):
    data = path.read_bytes()
    encoding = "utf-16" if data.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig"
    text = data.decode(encoding)
    if "BUILD SUCCESSFUL" not in text or "BUILD FAILED" in text:
        raise ValueError("완료된 빌드 성공 로그가 필요합니다.")
    import xml.etree.ElementTree as ET
    totals = collections.Counter()
    for report_path in (ROOT / "build" / "test-results" / "test").glob("TEST-*.xml"):
        report = ET.parse(report_path).getroot()
        for key in ("tests", "failures", "errors", "skipped"):
            totals[key] += int(report.get(key, 0))
    if not totals["tests"] or totals["failures"] or totals["errors"]:
        raise ValueError("실패 없이 완료된 테스트 결과가 필요합니다.")
    return dict(totals)


def export_review(cases_path, checks_path):
    rows = read_rows(cases_path)
    if not rows or any(row.get("status") != "VERIFIED" for row in rows):
        raise ValueError("리뷰 회귀 검증이 모두 완료된 원시 자료가 필요합니다.")
    totals = check_build(checks_path)
    model_path = cases_path.with_name(cases_path.name.replace("review-regressions-", "model-", 1))
    if not model_path.is_file():
        raise FileNotFoundError(model_path)
    target = HERE / "results" / "V3-review-regressions"
    archives = [archive(cases_path, target / "raw" / "final-cases.jsonl.gz"),
                archive(model_path, target / "raw" / "final-model.jsonl.gz")]
    changed = subprocess.check_output(["git", "diff", "--name-only"], cwd=ROOT, encoding="utf-8").splitlines()
    tags = json.load(urllib.request.urlopen("http://localhost:11434/api/tags", timeout=10))
    groups = collections.Counter(row["fixture"]["id"] for row in rows)
    write_json(target / "metrics.json", {
        "baseCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, encoding="utf-8").strip(),
        "changesNotCommitted": True,
        "codeFileSha256": {name: hashlib.sha256((ROOT / name).read_bytes()).hexdigest()
                           for name in changed if name.endswith(".java")},
        "models": [{"name": model["name"], "digest": model["digest"]} for model in tags["models"]],
        "archives": archives, "cases": len(rows), "verified": len(rows), "fixtures": dict(groups),
        "distinctFixtures": len(groups), "modelCalls": len(read_rows(model_path)), "automatedTests": totals,
        "buildCommand": "gradlew.bat clean test build --build-cache",
        "notes": ["Spring 채팅 API와 실제 EXAONE 및 FAQ 검색을 사용한 문맥 연결 회귀 검증",
                  "VERIFIED는 대상 복원, 문맥 격리, 검색 및 실행 상태 검증이며 최종 답변 정확도 점수가 아님",
                  "반복 10건은 서로 독립적인 사용자 질문 10건이 아님", "V1과 V2 결과는 변경하지 않음"],
    })


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--summary", type=Path)
    parser.add_argument("--api", type=Path)
    parser.add_argument("--boundaries", type=Path)
    parser.add_argument("--review", type=Path, help="V3 리뷰 회귀 결과만 별도 보존")
    parser.add_argument("--checks", required=True, type=Path)
    args = parser.parse_args()
    for key, value in vars(args).items():
        if value is not None:
            setattr(args, key, value.resolve())
    if args.review is not None:
        export_review(args.review, args.checks)
        return
    if any(value is None for value in (args.summary, args.api, args.boundaries)):
        parser.error("기존 평가 내보내기에는 --summary, --api, --boundaries가 필요합니다.")
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
    totals = check_build(args.checks)
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
        "selectedInputs": {key: str(value) for key, value in vars(args).items() if value is not None},
        "faqSnapshot": json.loads((ROOT / ".measure" / "telme121" / "faq-snapshot" / "manifest.json")
                                   .read_text(encoding="utf-8")),
        "liveApiRows": len(read_rows(args.api)), "boundaryRows": len(read_rows(args.boundaries)),
        "buildCommand": "gradlew.bat clean test build --build-cache",
    })


if __name__ == "__main__":
    main()
