"""저장한 Spring 채팅 원시 결과를 집계한다. 모델은 호출하지 않는다."""
import argparse
import collections
import hashlib
import json
import pathlib
import statistics
import math
import tarfile

ROOT = pathlib.Path(__file__).resolve().parents[2]


def load(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def percentile95(values):
    return sorted(values)[math.ceil(len(values) * .95) - 1]


def classify(row):
    trace = row.get("trace", {}).get("questionResolution", {})
    sources = trace.get("sourceMessageIds", [])
    answer = next((x["content"] for x in row.get("messages", [])
                   if x["message_id"] == row.get("execution", {}).get("output_message_id")), "") or ""
    relation = trace.get("relation")
    if not relation:
        clarification = trace.get("needsClarification", False) or answer.startswith("어떤 내용에 대한 질문인지 확인이 필요합니다.")
        relation = "CLARIFICATION_REQUIRED" if clarification else "HISTORY_DEPENDENT" if sources else "SELF_CONTAINED"
    original = row.get("sourceMessageIds", [])
    indexes = [original.index(source) + 1 if source in original else -1 for source in sources]
    valid = "error" not in row and trace.get("status") not in {"INVALID_RESOLUTION", "INVALID_CONTEXT"}
    if row.get("execution", {}).get("status") != "COMPLETED":
        valid = False
    return relation, indexes, valid


def summarize(directory, fixture_key="cases"):
    rows = load(directory / "cases.jsonl")
    fixtures = json.loads((ROOT / "scripts/context-resolution-evaluation/cases.json").read_text(encoding="utf-8"))[fixture_key]
    assert len(rows) == len(fixtures)
    assert [x["fixture"] for x in rows] == fixtures
    calls = load(directory / "model.jsonl")
    groups = collections.defaultdict(lambda: dict(total=0, matched=0))
    splits = collections.defaultdict(lambda: dict(total=0, matched=0))
    failures = []
    counters = collections.Counter()
    decisions = {}
    for row in rows:
        fixture = row["fixture"]
        actual, selected, valid = classify(row)
        expected = fixture["expectedRelation"]
        matched = valid and actual == expected and selected == fixture["expectedSourceIndexes"]
        decisions[fixture["id"]] = (actual, selected)
        key = "variant" if "variantOf" in fixture else "base"
        counters[key + "Total"] += 1
        counters[key + "Matched"] += matched
        if not valid:
            counters["validationOrPipelineFailure"] += 1
        if expected == "SELF_CONTAINED" and actual == "CLARIFICATION_REQUIRED":
            counters["independentBlocked"] += 1
        if expected != "SELF_CONTAINED" and actual == "SELF_CONTAINED":
            counters["dependentOrAmbiguousPassed"] += 1
        if actual == "HISTORY_DEPENDENT" and (expected != actual or selected != fixture["expectedSourceIndexes"]):
            counters["wrongSources"] += 1
        if expected == "HISTORY_DEPENDENT" and any(x not in selected for x in fixture["expectedSourceIndexes"]):
            counters["missingRequiredSources"] += 1
        if key == "base":
            for aggregate in [groups[fixture["group"]], splits[fixture["split"]]]:
                aggregate["total"] += 1
                aggregate["matched"] += matched
        if not matched:
            failures.append(dict(id=fixture["id"], split=fixture["split"], question=fixture["question"],
                                 expected=expected, actual=actual, expectedIndexes=fixture["expectedSourceIndexes"],
                                 actualIndexes=selected, valid=valid))
    inconsistent = [f["id"] for f in fixtures if "variantOf" in f and decisions[f["id"]] != decisions[f["variantOf"]]]
    context = [x for x in calls if x["request"]["taskType"] == "CONTEXT_RESOLUTION"]
    reference = [x for x in context if x["request"].get("promptVersion", "").endswith("-reference")]
    linkage = [x for x in context if x["request"].get("promptVersion", "").endswith("-sources")]
    fallback = [x for x in context if x["request"].get("promptVersion", "").endswith("-reference-fallback")]
    context_by_case = []
    call_counts = collections.Counter()
    for row in rows:
        scoped = [call for call in context if call["request"].get("executionId") == row.get("executionId")]
        context_by_case.append(sum(call["elapsedMillis"] for call in scoped))
        call_counts[str(len(scoped))] += 1
    latency = [x["elapsedMillis"] for x in context]
    cases_latency = [x["elapsedMillis"] for x in rows]
    return dict(counts=dict(counters), groups=dict(groups), splits=dict(splits), failures=failures,
                spacingInconsistencies=inconsistent, calls=dict(collections.Counter(x["request"]["taskType"] for x in calls)),
                contextStages=dict(referenceCalls=len(reference), linkageCalls=len(linkage), fallbackCalls=len(fallback)),
                contextCallsPerCase=dict(call_counts),
                contextCaseLatency=dict(medianMillis=statistics.median(context_by_case), p95Millis=percentile95(context_by_case)),
                contextLatency=dict(medianMillis=statistics.median(latency), p95Millis=percentile95(latency)),
                chatLatency=dict(medianMillis=statistics.median(cases_latency), p95Millis=percentile95(cases_latency),
                                 totalMillis=sum(cases_latency)))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=pathlib.Path, required=True)
    parser.add_argument("--improved", type=pathlib.Path, required=True)
    parser.add_argument("--confirmation", type=pathlib.Path)
    parser.add_argument("--heldout", type=pathlib.Path)
    parser.add_argument("--previous", type=pathlib.Path)
    parser.add_argument("--source-selection", type=pathlib.Path)
    args = parser.parse_args()
    report = dict(baseline=summarize(args.baseline), improved=summarize(args.improved))
    if args.confirmation:
        report["confirmation"] = summarize(args.confirmation, "confirmationCases")
    if args.heldout:
        report["heldout"] = summarize(args.heldout, "evidenceValidationCases")
    if args.previous:
        report["previous"] = summarize(args.previous)
    if args.source_selection:
        report["sourceSelection"] = summarize(args.source_selection, "sourceSelectionCases")
    out = ROOT / "scripts/context-resolution-evaluation/runs"
    out.mkdir(parents=True, exist_ok=True)
    files = []
    archive = out / "comparison.tar.gz"
    with tarfile.open(archive, "w:gz") as tar:
        primary = [("baseline", args.baseline), ("improved", args.improved)]
        if args.confirmation:
            primary.append(("confirmation", args.confirmation))
        if args.heldout:
            primary.append(("heldout", args.heldout))
        if args.previous:
            primary.append(("previous-v18", args.previous))
        if args.source_selection:
            primary.append(("source-selection", args.source_selection))
        for label, directory in primary:
            for filename in ["cases.jsonl", "model.jsonl"]:
                path = directory / filename
                tar.add(path, arcname=label + "/" + filename)
                files.append(dict(name=label + "/" + filename, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        # 개발 중간 결과와 기존 회귀 검사도 같은 압축 자료에 보존한다.
        work = ROOT / ".measure/telme139"
        for directory in sorted(work.glob("development-v*")):
            for path in directory.glob("*.jsonl"):
                name = "development/" + directory.name.removeprefix("development-") + "/" + path.name
                tar.add(path, arcname=name)
                files.append(dict(name=name, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        for directory in sorted(work.glob("final-v*")):
            if directory.resolve() == args.improved.resolve():
                continue
            for path in directory.glob("*.jsonl"):
                name = "candidates/" + directory.name.removeprefix("final-") + "/" + path.name
                tar.add(path, arcname=name)
                files.append(dict(name=name, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        pilot = work / "improved"
        for path in pilot.glob("*.jsonl"):
            name = "candidate-v9/" + path.name
            tar.add(path, arcname=name)
            files.append(dict(name=name, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        for directory in sorted(work.glob("evidence-development-v*")):
            for path in directory.glob("*.jsonl"):
                name = "development/" + directory.name + "/" + path.name
                tar.add(path, arcname=name)
                files.append(dict(name=name, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        for directory in [work / "confirmation-complete", work / "confirmation-v17", work / "confirmation-v18"]:
            for path in directory.glob("*.jsonl"):
                name = "previous/" + directory.name + "/" + path.name
                tar.add(path, arcname=name)
                files.append(dict(name=name, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        for path in sorted(work.glob("v*-code.zip")):
            name = "code-snapshots/" + path.name
            tar.add(path, arcname=name)
            files.append(dict(name=name, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        before = work / "before-evidence-change.zip"
        if before.exists():
            tar.add(before, arcname="previous/before-evidence-change.zip")
            files.append(dict(name="previous/before-evidence-change.zip", sha256=hashlib.sha256(before.read_bytes()).hexdigest()))
        for directory in sorted(work.glob("evidence-*-v*")):
            if any(directory.resolve() == primary_dir.resolve() for _, primary_dir in primary):
                continue
            if directory.name.startswith("evidence-development-"):
                continue
            for path in directory.glob("*.jsonl"):
                name = "candidates/" + directory.name + "/" + path.name
                tar.add(path, arcname=name)
                files.append(dict(name=name, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        # 최초 결과에 사용한 회귀 원시 자료도 새 자료와 구분해 유지한다.
        if before.exists():
            import zipfile
            with zipfile.ZipFile(before) as old_zip:
                previous_manifest = json.loads(old_zip.read(".measure/telme139/manifest.json"))
            previous_paths = [(name, ROOT / item["file"]) for name, item in previous_manifest.get("regressions", {}).items()]
            previous_model = previous_manifest.get("regressionModelFile")
            if previous_model:
                previous_paths.append(("model", ROOT / previous_model))
            for label, path in previous_paths:
                if path.exists():
                    name = "previous/regressions/" + label + ".jsonl"
                    tar.add(path, arcname=name)
                    files.append(dict(name=name, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        manifest = work / "manifest.json"
        if manifest.exists():
            tar.add(manifest, arcname="manifest.json")
            report["environment"] = json.loads(manifest.read_text(encoding="utf-8"))
            for group, entry in report["environment"].get("regressions", {}).items():
                path = ROOT / entry["file"]
                name = "regressions/" + group + ".jsonl"
                tar.add(path, arcname=name)
                files.append(dict(name=name, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
            for entry in report["environment"].get("developmentRegressionAttempts", []):
                path = ROOT / entry["file"]
                name = "development/regression-attempts/" + path.name
                tar.add(path, arcname=name)
                files.append(dict(name=name, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
            model_file = report["environment"].get("regressionModelFile")
            if model_file:
                regression_model = ROOT / model_file
                tar.add(regression_model, arcname="regressions/model.jsonl")
                files.append(dict(name="regressions/model.jsonl", sha256=hashlib.sha256(regression_model.read_bytes()).hexdigest()))
    report["rawFiles"] = files
    report["fixtureSha256"] = hashlib.sha256((ROOT / "scripts/context-resolution-evaluation/cases.json").read_bytes()).hexdigest()
    (out / "metrics.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({key: value["counts"] for key, value in report.items() if key in {"baseline", "improved"}}, ensure_ascii=False))


if __name__ == "__main__":
    main()
