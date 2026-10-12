"""별도 평가 DB의 실제 Spring 채팅 API로 검색어 보존과 재검색을 확인한다."""

import argparse
import gzip
import hashlib
import http.cookiejar
import importlib.util
import json
import subprocess
import threading
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


CASES = [
    ("LOST", ["유심을 잃어버렸어요"], "일반 분실 처리 안내. 재발급만 요청한 것으로 좁히지 않음"),
    ("LOST-SPACING", ["유심을잃어버렸어요"], "LOST와 같은 업무와 조건"),
    ("ORIGINAL-HIT", ["유심 재발급 시 필요한 서류를 알려주세요."], "원문 검색 성공 시 재검색 생략"),
    ("PORTIN-CONDITION", ["미납 요금이 있는데 번호이동하고 싶어요"], "미납 조건을 유지한 안내 또는 정상 조건 되묻기"),
    ("NEGATION", ["해지는 원하지 않고 요금만 줄이고 싶어요"], "해지 신청 질문으로 바꾸지 않음"),
    ("COMPARISON", ["명의 변경과 번호 이동의 필요 서류를 비교해줘"], "비교 두 대상을 유지한 단일 상담"),
    ("COMPOUND", ["명의 변경에 필요한 서류와 번호 이동에 필요한 서류를 각각 알려줘"], "두 FAQ를 각각 검색"),
    ("MULTITURN", ["유심 재발급 비용은 얼마인가요?", "그건 어디서 신청해?"], "이전 유심 재발급 대상을 복원"),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", required=True)
    parser.add_argument("--base-url", default="http://localhost:18135")
    parser.add_argument("--llm-log", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--source-root", type=Path)
    parser.add_argument("--label", default="current")
    args = parser.parse_args()
    if not args.database.startswith("telme_146_") or args.output.exists():
        parser.error("별도 telme_146_ DB와 새 출력 경로가 필요합니다.")
    repo = Path(__file__).resolve().parents[2]
    spec = importlib.util.spec_from_file_location("live", repo / "scripts/compound-faq-evaluation/run_live_api.py")
    live = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(live)
    source_root = args.source_root or repo
    if not (source_root / "src/main/java").is_dir():
        parser.error("해시를 기록할 운영 코드 경로가 필요합니다.")
    digest = hashlib.sha256()
    for path in sorted((source_root / "src/main/java").rglob("*.java")):
        digest.update(path.relative_to(source_root).as_posix().encode())
        digest.update(path.read_bytes())
    result = {"baseCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
              "sourceTreeSha256": digest.hexdigest(), "database": args.database,
              "startedAt": datetime.now(timezone.utc).isoformat(),
              "label": args.label, "scope": "검색어 보존과 재검색 및 소규모 회귀 확인", "cases": []}
    log_start = len(args.llm_log.read_text(encoding="utf-8").splitlines()) if args.llm_log.exists() else 0
    with urllib.request.urlopen("http://localhost:11434/api/tags", timeout=15) as response:
        result["models"] = json.load(response)
    args.output.parent.mkdir(parents=True, exist_ok=True)

    def save():
        calls = [json.loads(line) for line in args.llm_log.read_text(encoding="utf-8").splitlines()[log_start:]] \
            if args.llm_log.exists() else []
        result["modelCalls"] = [call for call in calls if call["startedAt"] >= result["startedAt"]]
        with gzip.open(args.output, "wt", encoding="utf-8") as output:
            json.dump(result, output, ensure_ascii=False)

    for case_id, questions, expectation in CASES:
        opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        sid = live.api(opener, args.base_url, "POST", "/api/v1/chat/sessions", {"title": case_id})["sessionId"]
        case = {"id": case_id, "expectation": expectation, "sessionId": sid, "turns": []}
        result["cases"].append(case)
        for question in questions:
            row = {"question": question, "startedAt": datetime.now(timezone.utc).isoformat(), "issues": []}
            case["turns"].append(row)
            started = time.monotonic()
            sent = live.api(opener, args.base_url, "POST", f"/api/v1/chat/sessions/{sid}/messages", {"content": question})
            row["sent"] = sent
            path = f"/api/v1/chat/sessions/{sid}/executions/{sent['executionId']}"
            events, errors = [], []
            listener = threading.Thread(target=live.subscribe,
                args=(opener, args.base_url + path + "/subscribe", events, errors), daemon=True)
            listener.start()
            deadline = time.monotonic() + 240
            while True:
                trace = live.api(opener, args.base_url, "GET", path + "/trace")
                if trace["status"] != "RUNNING":
                    break
                if time.monotonic() > deadline:
                    raise TimeoutError(f"{case_id}: execution still running")
                time.sleep(0.3)
            listener.join(timeout=10)
            row.update(trace=trace, history=live.api(opener, args.base_url, "GET",
                        f"/api/v1/chat/sessions/{sid}/messages?size=50"),
                       database=live.database_snapshot(args.database, sid, "telme-flow-postgres"),
                       sseEvents=events, sseErrors=errors, elapsedMs=round((time.monotonic()-started)*1000))
            answers = [item for item in row["history"]["messages"]
                       if item["role"] == "ASSISTANT" and item["messageId"] == trace.get("outputMessageId")]
            row["output"] = answers[0] if answers else None
            if trace["status"] != "COMPLETED" or not answers:
                row["issues"].append("실행 미완료 또는 저장 답변 없음")
            if answers:
                row["sources"] = live.api(opener, args.base_url, "GET",
                    f"/api/v1/chat/messages/{answers[0]['messageId']}/sources")
                tokens = "".join(event["data"] for event in events if event["event"] == "token")
                complete = [json.loads(event["data"]) for event in events if event["event"] == "complete"]
                if tokens and tokens != (answers[0].get("content") or ""):
                    row["issues"].append("SSE 토큰과 저장 본문 불일치")
                if not complete or complete[-1].get("outputMessage", {}).get("messageId") != answers[0]["messageId"]:
                    row["issues"].append("SSE 완료 메시지 ID 불일치")
            if errors or listener.is_alive():
                row["issues"].append("SSE 종료 오류")
            save()
            print(case_id, row["trace"]["status"], row["elapsedMs"], "ms", flush=True)
    result["completedAt"] = datetime.now(timezone.utc).isoformat()
    save()


if __name__ == "__main__":
    main()
