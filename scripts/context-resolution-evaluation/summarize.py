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
    latency = [x["elapsedMillis"] for x in context]
    cases_latency = [x["elapsedMillis"] for x in rows]
    return dict(counts=dict(counters), groups=dict(groups), splits=dict(splits), failures=failures,
                spacingInconsistencies=inconsistent, calls=dict(collections.Counter(x["request"]["taskType"] for x in calls)),
                contextLatency=dict(medianMillis=statistics.median(latency), p95Millis=percentile95(latency)),
                chatLatency=dict(medianMillis=statistics.median(cases_latency), p95Millis=percentile95(cases_latency),
                                 totalMillis=sum(cases_latency)))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=pathlib.Path, required=True)
    parser.add_argument("--improved", type=pathlib.Path, required=True)
    parser.add_argument("--confirmation", type=pathlib.Path)
    args = parser.parse_args()
    report = dict(baseline=summarize(args.baseline), improved=summarize(args.improved))
    if args.confirmation:
        report["confirmation"] = summarize(args.confirmation, "confirmationCases")
    out = ROOT / "scripts/context-resolution-evaluation/runs"
    out.mkdir(parents=True, exist_ok=True)
    files = []
    archive = out / "comparison.tar.gz"
    with tarfile.open(archive, "w:gz") as tar:
        primary = [("baseline", args.baseline), ("improved", args.improved)]
        if args.confirmation:
            primary.append(("confirmation", args.confirmation))
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
        manifest = work / "manifest.json"
        if manifest.exists():
            tar.add(manifest, arcname="manifest.json")
            report["environment"] = json.loads(manifest.read_text(encoding="utf-8"))
            for group, entry in report["environment"].get("regressions", {}).items():
                path = ROOT / entry["file"]
                name = "regressions/" + group + ".jsonl"
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
