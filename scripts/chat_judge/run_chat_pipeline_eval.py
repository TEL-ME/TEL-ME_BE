"""실제 Spring 채팅 API로 평가셋 질문의 답변을 모으고(캡처), 로컬 vLLM으로 판정한다. Bedrock 판정은 run_bedrock_judge.py로 한다."""

import argparse
import copy
import http.client
from datetime import datetime
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.error
import urllib.request

from scripts.chat_judge.evaluate_chat_pipeline import checkpoint, evaluate, load_json, report
from scripts.chat_judge.export_chat_pipeline_review import review_report
from scripts.chat_judge import judge_chat_flow as judge


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_FIXTURE = ROOT / "scripts/chat_judge/data/chat_pipeline_quality_v2_2.json"
DATABASE_NAME = re.compile(r"telme_judge_[a-z0-9_]{1,40}")


def select_cases(cases, limit=0):
    """흐름 회귀 대화는 모두 두고, 나머지는 분야·질문 유형을 돌아가며 limit개까지 고른다(짧은 확인 실행용)."""
    if limit <= 0 or limit >= len(cases):
        return copy.deepcopy(cases)
    regression = [case for case in cases if case.get("suite") == "flow_regression"]
    if limit < len(regression):
        raise ValueError(f"limit must be at least {len(regression)} to preserve flow scenarios")
    selected = list(regression)
    buckets = {}
    for case in cases:
        if case.get("suite") != "flow_regression":
            buckets.setdefault((case["category"], case["questionType"]), []).append(case)
    while len(selected) < limit and buckets:
        for key in list(buckets):
            if len(selected) >= limit:
                break
            selected.append(buckets[key].pop(0))
            if not buckets[key]:
                del buckets[key]
    return copy.deepcopy(selected)


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


def ensure_database(container, database="telme_judge_eval"):
    command(["docker", "start", container], stdout=subprocess.DEVNULL)
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        if subprocess.run(["docker", "exec", container, "pg_isready", "-U", "telme"],
                          capture_output=True).returncode == 0:
            break
        time.sleep(1)
    found = command(["docker", "exec", container, "psql", "-U", "telme", "-d", "postgres", "-Atc",
                     f"SELECT 1 FROM pg_database WHERE datname = '{database}'"], capture_output=True).stdout
    if found.strip() != b"1":
        command(["docker", "exec", container, "psql", "-U", "telme", "-d", "postgres", "-v", "ON_ERROR_STOP=1",
                 "-c", f"CREATE DATABASE {database}"])
        dump = command(["docker", "exec", container, "pg_dump", "-U", "telme", "-d", "telme"], capture_output=True).stdout
        command(["docker", "exec", "-i", container, "psql", "-U", "telme", "-d", database,
                 "-v", "ON_ERROR_STOP=1"], input=dump, stdout=subprocess.DEVNULL)
    counts = command(["docker", "exec", container, "psql", "-U", "telme", "-d", database, "-Atc",
                     "SELECT (SELECT count(*) FROM faqs), (SELECT count(*) FROM faq_embeddings)"],
                    capture_output=True).stdout.decode().strip()
    if not counts or any(int(value) < 1150 for value in counts.split("|")):
        raise RuntimeError(f"Evaluation DB must have FAQ and embeddings: {counts}")
    return counts


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fixture", type=Path, default=DEFAULT_FIXTURE)
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
    # 측정마다 새 평가 DB를 쓰려면 telme_judge_로 시작하는 이름을 준다. 없으면 --postgres-container의 telme DB를 복제한다
    parser.add_argument("--database", default="telme_judge_eval")
    # 앱 안 Bedrock 호출의 원본 요청·응답(상담 내용)까지 캡처에 남긴다. 로컬 분석용이라 결과 묶음에 넣지 않는다
    parser.add_argument("--record-raw", action="store_true")
    args = parser.parse_args()
    # 이름을 SQL에 그대로 넣으므로 소문자·숫자·밑줄만 받는다("telme_judge_2026-10-22"는 CREATE DATABASE에서 문법 오류)
    if not DATABASE_NAME.fullmatch(args.database):
        parser.error("--database must be telme_judge_ followed by 1-40 lowercase letters, digits or underscores")
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
    for relative in ("scripts/chat_judge/run_chat_pipeline_eval.py", "scripts/chat_judge/evaluate_chat_pipeline.py",
                     "scripts/chat_judge/judge_chat_flow.py", "scripts/chat_judge/service_data.py",
                     "src/test/java/com/telme/probe/ChatJudgeCaptureProbe.java"):
        data = (ROOT / relative).read_bytes()
        snapshot = args.out / "source" / relative
        snapshot.parent.mkdir(parents=True, exist_ok=True)
        snapshot.write_bytes(data)
        manifest["evaluationSourceFiles"][relative] = judge.sha256(data)
    if not args.capture:
        if not args.fixture.exists():
            raise SystemExit(f"평가셋이 없다: {args.fixture}. build_service_quality_set이나 build_service_flow_set으로 만든다")
        fixtures = select_cases(load_json(args.fixture), args.limit)
        # 지명으로 찾는 매장 검색은 카카오 로컬 API가 있어야 한다. 키가 없으면 그 대화는 빼고 기록한다
        if not (os.environ.get("KAKAO_REST_API_KEY") or os.environ.get("KAKAO_CLIENT_ID")):
            manifest["skippedCases"] = [{"caseId": case["caseId"], "reason": "KAKAO_NOT_CONFIGURED"}
                                        for case in fixtures if case.get("requiresKakao")]
            fixtures = [case for case in fixtures if not case.get("requiresKakao")]
        fixture_path = args.out / "questions.json"
        checkpoint(fixture_path, fixtures)
        manifest["fixtureSha256"] = judge.sha256(fixtures)
        manifest["plannedConversations"] = len(fixtures)
        manifest["plannedTurns"] = sum(len(case["turns"]) for case in fixtures)
        checkpoint(args.out / "manifest.json", manifest)
        print(f"[pipeline] Capturing {manifest['plannedTurns']} real chat questions", flush=True)
        manifest["databaseCounts"] = ensure_database(args.postgres_container, args.database)
        manifest["database"] = args.database
        manifest["recordRaw"] = args.record_raw
        command(["docker", "stop", args.vllm_container], stdout=subprocess.DEVNULL)
        command(["docker", "start", args.generator_container], stdout=subprocess.DEVNULL)
        wait_http("http://localhost:11434/api/tags", 90)
        env = dict(os.environ)
        env.update({"TELME_CHAT_JUDGE_PROBE": "true", "TELME_CHAT_JUDGE_FIXTURE": str(fixture_path),
                    "TELME_CHAT_JUDGE_OUT": str(capture_path), "POSTGRES_DB": args.database,
                    "TELME_CHAT_JUDGE_RAW": "true" if args.record_raw else "false",
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
