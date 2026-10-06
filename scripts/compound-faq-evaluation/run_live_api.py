"""별도 로컬 평가 DB에서 실제 채팅 API 흐름을 확인한다."""

import argparse
import hashlib
import http.cookiejar
import json
import subprocess
import threading
import time
import urllib.request
from datetime import datetime, timezone
from pathlib import Path


CASES = [
    ("API-SINGLE", "물리 유심 재발급 비용은 얼마인가요?", 1, "GROUNDED", ["7700"]),
    ("API-MULTI", "명의 변경에 필요한 서류와 번호 이동에 필요한 서류를 각각 알려줘",
     2, "GROUNDED", ["신분증", "미납"]),
    ("API-PARTIAL", "명의 변경에 필요한 서류와 명의 변경 시 무료 해외 항공권을 주는지 각각 알려줘",
     2, "GROUNDED", ["신분증", "안내드릴 수 있는 정보가 없습니다."]),
    ("API-COMPARE-DOCS", "명의 변경과 번호 이동의 필요 서류를 비교해줘",
     1, "GROUNDED", ["신분증", "미납"]),
    ("API-COMPARE-PLAN", "5G 라이트와 LTE 플러스의 월 요금과 데이터 제공량을 비교해줘",
     1, "GROUNDED", ["45000", "49000", "8GB", "15GB"]),
    ("API-NO-EVIDENCE", "너겟 5G 요금제와 LTE 요금제의 데이터 제공량 차이를 비교해줘",
     1, "NO_EVIDENCE", []),
    ("API-BOTH", "요금제 종류 알려주고 강남역 근처 매장 찾아줘",
     0, None, ["질문을 하나씩 나누어"]),
]


def api(opener, base, method, path, body=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(base + path, data=data, method=method,
                                     headers={"Content-Type": "application/json"})
    with opener.open(request, timeout=30) as response:
        return json.load(response)["result"]


def subscribe(opener, url, events, errors):
    try:
        with opener.open(urllib.request.Request(url, headers={"Accept": "text/event-stream"}),
                         timeout=240) as response:
            name, data = None, []
            for raw in response:
                line = raw.decode("utf-8").rstrip("\r\n")
                if line.startswith("event:"):
                    name = line[6:].lstrip(" ")
                elif line.startswith("data:"):
                    value = line[5:]
                    data.append(value[1:] if value.startswith(" ") else value)
                elif not line and name:
                    events.append({"event": name, "data": "\n".join(data)})
                    if name in ("complete", "error"):
                        return
                    name, data = None, []
    except Exception as error:
        errors.append(f"{type(error).__name__}: {error}")


def database_snapshot(database, session_id):
    query = f"""
        SELECT jsonb_build_object(
          'consultations', COALESCE((SELECT jsonb_agg(jsonb_build_object(
              'consultRequestId', consult_request_id, 'status', status, 'queryText', query_text,
              'order', subquery_order, 'intent', intent) ORDER BY subquery_order)
              FROM consult_requests WHERE session_id={int(session_id)}), '[]'::jsonb),
          'assistantCount', (SELECT count(*) FROM chat_messages
              WHERE session_id={int(session_id)} AND role='ASSISTANT'));
    """
    command = ["docker", "exec", "-i", "telme-postgres", "psql", "-U", "telme",
               "-d", database, "-At", "--no-psqlrc", "-v", "ON_ERROR_STOP=1"]
    result = subprocess.run(command, input=query, encoding="utf-8", capture_output=True,
                            check=True, timeout=15)
    return json.loads(result.stdout)


def run_case(args, case):
    case_id, question, expected_count, basis, facts = case
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    session = api(opener, args.base_url, "POST", "/api/v1/chat/sessions", {"title": case_id})
    sid = session["sessionId"]
    sent = api(opener, args.base_url, "POST", f"/api/v1/chat/sessions/{sid}/messages", {"content": question})
    execution_id = sent["executionId"]
    path = f"/api/v1/chat/sessions/{sid}/executions/{execution_id}"
    events, stream_errors = [], []
    listener = threading.Thread(target=subscribe,
                                args=(opener, args.base_url + path + "/subscribe", events, stream_errors),
                                daemon=True)
    listener.start()
    deadline = time.monotonic() + 210
    while True:
        trace = api(opener, args.base_url, "GET", path + "/trace")
        if trace["status"] != "RUNNING":
            break
        if time.monotonic() > deadline:
            raise TimeoutError(f"{case_id}: execution still running")
        time.sleep(0.3)
    listener.join(timeout=10)
    history = api(opener, args.base_url, "GET", f"/api/v1/chat/sessions/{sid}/messages?size=50")
    assistant = [item for item in history["messages"] if item["role"] == "ASSISTANT"]
    row = {"id": case_id, "question": question, "sessionId": sid, "executionId": execution_id,
           "trace": trace, "history": history, "sseEvents": events, "sseErrors": stream_errors,
           "database": database_snapshot(args.database, sid), "issues": []}
    issues = row["issues"]
    if trace["status"] != "COMPLETED" or len(assistant) != 1:
        issues.append("execution must complete with one assistant message")
    if len(row["database"]["consultations"]) != expected_count:
        issues.append(f"expected {expected_count} consultations")
    if any(item["status"] != "DONE" for item in row["database"]["consultations"]):
        issues.append("consultation did not complete")
    if assistant:
        answer = assistant[0]["content"] or ""
        if basis and assistant[0].get("answerBasis") != basis:
            issues.append(f"expected answer basis {basis}")
        normalized = answer.replace(",", "").replace(" ", "")
        if any(fact.replace(" ", "") not in normalized for fact in facts):
            issues.append("required answer fact missing")
        tokens = "".join(event["data"] for event in events if event["event"] == "token")
        if tokens and tokens != answer:
            issues.append("SSE tokens differ from saved answer")
        complete = [json.loads(event["data"]) for event in events if event["event"] == "complete"]
        if not complete or complete[-1]["outputMessage"]["messageId"] != assistant[0]["messageId"]:
            issues.append("SSE completion references a different saved message")
        if listener.is_alive() or stream_errors:
            issues.append("SSE did not terminate cleanly")
        sources = None
        for _ in range(20):
            sources = api(opener, args.base_url, "GET",
                          f"/api/v1/chat/messages/{assistant[0]['messageId']}/sources")
            if basis != "GROUNDED" or sources["sources"]:
                break
            time.sleep(0.1)
        row["sources"] = sources
        if basis == "GROUNDED" and not sources["sources"]:
            issues.append("grounded answer has no saved sources")
        if basis == "NO_EVIDENCE" and sources["sources"]:
            issues.append("missing-evidence answer unexpectedly has sources")
    return row


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:18089")
    parser.add_argument("--database", required=True)
    parser.add_argument("--out", type=Path,
                        default=Path("scripts/compound-faq-evaluation/runs/live-api-current.json"))
    args = parser.parse_args()
    if not args.database.startswith("telme_compound_pr_review"):
        parser.error("use an isolated telme_compound_pr_review database")
    started = time.monotonic()
    repo = Path(__file__).resolve().parents[2]
    digest = hashlib.sha256()
    for source in sorted((repo / "src/main/java").rglob("*.java")):
        digest.update(str(source.relative_to(repo)).encode())
        digest.update(source.read_bytes())
    result = {"recordedAt": datetime.now(timezone.utc).isoformat(),
              "sourceTreeSha256": digest.hexdigest(), "model": "exaone3.5:7.8b",
              "database": args.database, "cases": []}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    for case in CASES:
        case_started = time.monotonic()
        try:
            row = run_case(args, case)
        except Exception as error:
            row = {"id": case[0], "question": case[1], "issues": [f"{type(error).__name__}: {error}"]}
        row["elapsedSeconds"] = round(time.monotonic() - case_started, 3)
        result["cases"].append(row)
        result["elapsedSeconds"] = round(time.monotonic() - started, 3)
        args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        print(case[0], "PASS" if not row["issues"] else row["issues"], flush=True)
    raise SystemExit(1 if any(row["issues"] for row in result["cases"]) else 0)


if __name__ == "__main__":
    main()
