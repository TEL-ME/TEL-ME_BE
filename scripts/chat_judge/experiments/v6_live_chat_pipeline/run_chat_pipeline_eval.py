"""Collect real Spring chat answers, then evaluate them on parallel vLLM."""

import argparse
import http.client
from datetime import datetime
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import urllib.error
import urllib.request

from scripts.chat_judge.experiments.v6_live_chat_pipeline.build_chat_pipeline_eval import DEFAULT_OUTPUT, ROOT, select_cases
from scripts.chat_judge.experiments.v6_live_chat_pipeline.evaluate_chat_pipeline import checkpoint, evaluate, load_json, report
from scripts.chat_judge.experiments.v6_live_chat_pipeline.export_chat_pipeline_review import review_report
from scripts.chat_judge import judge_chat_flow as judge


def command(args, **kwargs):
    return subprocess.run(args, check=True, cwd=ROOT, **kwargs)


def ensure_docker():
    ready = subprocess.run(["docker", "info", "--format", "{{.ServerVersion}}"],
                           capture_output=True, text=True)
    if ready.returncode == 0:
        return
    desktop = Path("C:/Program Files/Docker/Docker/Docker Desktop.exe")
    if os.name == "nt" and desktop.exists():
        command(["powershell", "-NoProfile", "-Command",
                 "Start-Process -FilePath 'C:/Program Files/Docker/Docker/Docker Desktop.exe' -WindowStyle Hidden"])
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            if subprocess.run(["docker", "info"], capture_output=True).returncode == 0:
                return
            time.sleep(2)
    raise RuntimeError("Docker engine did not start")


def wait_http(url, seconds=240):
    deadline = time.monotonic() + seconds
    last = None
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=10) as response:
                return json.load(response)
        except (urllib.error.URLError, OSError, http.client.HTTPException, ValueError) as error:
            last = error
            time.sleep(2)
    raise RuntimeError(f"Service did not become ready: {url}: {last}")


def ensure_database(container):
    command(["docker", "start", container], stdout=subprocess.DEVNULL)
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        if subprocess.run(["docker", "exec", container, "pg_isready", "-U", "telme"],
                          capture_output=True).returncode == 0:
            break
        time.sleep(1)
    found = command(["docker", "exec", container, "psql", "-U", "telme", "-d", "postgres", "-Atc",
                     "SELECT 1 FROM pg_database WHERE datname = 'telme_judge_eval'"], capture_output=True).stdout
    if found.strip() != b"1":
        command(["docker", "exec", container, "psql", "-U", "telme", "-d", "postgres", "-v", "ON_ERROR_STOP=1",
                 "-c", "CREATE DATABASE telme_judge_eval"])
        dump = command(["docker", "exec", container, "pg_dump", "-U", "telme", "-d", "telme"], capture_output=True).stdout
        command(["docker", "exec", "-i", container, "psql", "-U", "telme", "-d", "telme_judge_eval",
                 "-v", "ON_ERROR_STOP=1"], input=dump, stdout=subprocess.DEVNULL)
    counts = command(["docker", "exec", container, "psql", "-U", "telme", "-d", "telme_judge_eval", "-Atc",
                     "SELECT (SELECT count(*) FROM faqs), (SELECT count(*) FROM faq_embeddings)"],
                    capture_output=True).stdout.decode().strip()
    if not counts or any(int(value) < 1150 for value in counts.split("|")):
        raise RuntimeError(f"Evaluation DB must have FAQ and embeddings: {counts}")
    return counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--limit", type=int, default=0, help="0: full suite; >=8: stratified smaller run")
    parser.add_argument("--out", type=Path, default=ROOT / ".measure" / ("chat-pipeline-" + datetime.now().strftime("%Y%m%d-%H%M%S")))
    parser.add_argument("--capture", type=Path, help="Judge an existing actual API capture instead of regenerating")
    parser.add_argument("--capture-only", action="store_true")
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--vllm-url", default="http://localhost:8001")
    parser.add_argument("--model", default="qwen3-14b-awq")
    parser.add_argument("--vllm-container", default="telme-judge-vllm-bounded")
    parser.add_argument("--generator-container", default="telme-ollama")
    parser.add_argument("--postgres-container", default="telme-postgres")
    args = parser.parse_args()
    if args.out.exists():
        parser.error("Output directory exists; use a new directory to preserve raw results")
    if not 1 <= args.workers <= 8:
        parser.error("workers must be 1..8")
    args.out = args.out.resolve()
    args.out.mkdir(parents=True)
    ensure_docker()
    capture_path = args.capture.resolve() if args.capture else args.out / "capture.json"
    manifest = {"createdAt": datetime.now().isoformat(), "capturePath": str(capture_path),
                "datasetScope": "fixed_challenge_set_not_live_user_distribution",
                "judgeBackend": "vllm", "judgeModel": args.model, "workers": args.workers}
    manifest["evaluationSourceFiles"] = {}
    for relative in ("scripts/chat_judge/experiments/v6_live_chat_pipeline/run_chat_pipeline_eval.py", "scripts/chat_judge/experiments/v6_live_chat_pipeline/evaluate_chat_pipeline.py",
                     "scripts/chat_judge/judge_chat_flow.py", "scripts/chat_judge/experiments/v6_live_chat_pipeline/build_chat_pipeline_eval.py",
                     "src/test/java/com/telme/probe/ChatJudgeCaptureProbe.java"):
        data = (ROOT / relative).read_bytes()
        snapshot = args.out / "source" / relative
        snapshot.parent.mkdir(parents=True, exist_ok=True)
        snapshot.write_bytes(data)
        manifest["evaluationSourceFiles"][relative] = judge.sha256(data)
    if not args.capture:
        if not args.fixture.exists():
            command([sys.executable, "-X", "utf8", "-m", "scripts.chat_judge.experiments.v6_live_chat_pipeline.build_chat_pipeline_eval"])
        fixtures = select_cases(load_json(args.fixture), args.limit)
        fixture_path = args.out / "questions.json"
        checkpoint(fixture_path, fixtures)
        manifest["fixtureSha256"] = judge.sha256(fixtures)
        manifest["plannedConversations"] = len(fixtures)
        manifest["plannedTurns"] = sum(len(case["turns"]) for case in fixtures)
        checkpoint(args.out / "manifest.json", manifest)
        print(f"[pipeline] Capturing {manifest['plannedTurns']} real chat questions", flush=True)
        manifest["databaseCounts"] = ensure_database(args.postgres_container)
        command(["docker", "stop", args.vllm_container], stdout=subprocess.DEVNULL)
        command(["docker", "start", args.generator_container], stdout=subprocess.DEVNULL)
        wait_http("http://localhost:11434/api/tags", 90)
        env = dict(os.environ)
        env.update({"TELME_CHAT_JUDGE_PROBE": "true", "TELME_CHAT_JUDGE_FIXTURE": str(fixture_path),
                    "TELME_CHAT_JUDGE_OUT": str(capture_path), "POSTGRES_DB": "telme_judge_eval",
                    "LLM_PROVIDER": "ollama", "PYTHONIOENCODING": "utf-8"})
        gradle = str(ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew"))
        with (args.out / "capture-gradle.log").open("wb") as log:
            command([gradle, "test", "--tests", "com.telme.probe.ChatJudgeCaptureProbe", "--rerun-tasks"],
                    env=env, stdout=log, stderr=subprocess.STDOUT)
        if not load_json(capture_path).get("captureComplete"):
            raise RuntimeError("Actual API capture did not complete; raw partial capture is preserved")
        print(f"[pipeline] Actual answers saved: {capture_path}", flush=True)
    if args.capture_only:
        checkpoint(args.out / "manifest.json", manifest)
        return
    # The generation and judging models are not loaded together on a 16 GB GPU.
    command(["docker", "stop", args.generator_container], stdout=subprocess.DEVNULL)
    command(["docker", "start", args.vllm_container], stdout=subprocess.DEVNULL)
    wait_http(args.vllm_url.rstrip("/") + "/v1/models", 240)
    inspected = json.loads(command(["docker", "inspect", args.vllm_container], capture_output=True).stdout)[0]
    manifest["vllmContainer"] = {"image": inspected["Config"]["Image"], "imageId": inspected["Image"],
                                 "cmd": inspected["Config"]["Cmd"], "patchMounts": inspected["Mounts"]}
    manifest["vllmRuntimeVersion"] = command(["docker", "exec", args.vllm_container, "python3", "-c",
                                            "import vllm; print(vllm.__version__)"], capture_output=True).stdout.decode().strip()
    checkpoint(args.out / "manifest.json", manifest)
    raw_path = args.out / "judged-raw.json.gz"
    for relative, expected_hash in manifest["evaluationSourceFiles"].items():
        if judge.sha256((ROOT / relative).read_bytes()) != expected_hash:
            raise RuntimeError(f"Evaluation source changed during capture: {relative}; raw capture is preserved")
    result = evaluate(load_json(capture_path), load_json(judge.DEFAULT_CATALOG), args.vllm_url, args.model,
                      workers=args.workers, on_checkpoint=lambda value: checkpoint(raw_path, value))
    result["runtimeManifest"] = manifest
    checkpoint(raw_path, result)
    checkpoint(args.out / "scores.json", result["summary"])
    checkpoint(args.out / "category-scores.json", {"byCategory": result["byCategory"], "bySuite": result["bySuite"]})
    (args.out / "report.md").write_text(report(result), encoding="utf-8")
    (args.out / "review.md").write_text(review_report(result, load_json(capture_path)), encoding="utf-8")
    print(json.dumps(result["summary"], ensure_ascii=False, indent=2), flush=True)
    print(f"[pipeline] Report: {args.out / 'report.md'}", flush=True)


if __name__ == "__main__":
    main()
