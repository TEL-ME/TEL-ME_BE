"""별도 DB의 실제 채팅 API에서 FAQ와 매장 복합 요청을 확인한다."""

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


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", required=True)
    parser.add_argument("--base-url", default="http://localhost:18095")
    parser.add_argument("--postgres-container", default="telme-flow-postgres")
    parser.add_argument("--ollama-url", default="http://localhost:11434")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if not args.database.startswith("telme_135_"):
        parser.error("별도 telme_135_ 평가 DB가 필요합니다.")
    repo = Path(__file__).resolve().parents[2]
    spec = importlib.util.spec_from_file_location("live", repo / "scripts/compound-faq-evaluation/run_live_api.py")
    live = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(live)

    def sql(query):
        result = subprocess.run(["docker", "exec", "-i", args.postgres_container, "psql", "-U", "telme",
                                 "-d", args.database, "-At", "--no-psqlrc", "-v", "ON_ERROR_STOP=1"],
                                input=query, encoding="utf-8", capture_output=True, check=True, timeout=15)
        return result.stdout.strip()

    digest = hashlib.sha256()
    for path in sorted((repo / "src/main/java").rglob("*.java")):
        digest.update(path.relative_to(repo).as_posix().encode())
        digest.update(path.read_bytes())
    with urllib.request.urlopen(args.ollama_url + "/api/tags", timeout=15) as response:
        models = json.load(response)["models"]
    result = {"recordedAt": datetime.now(timezone.utc).isoformat(), "sourceTreeSha256": digest.hexdigest(),
              "models": models, "database": args.database, "baseUrl": args.base_url,
              "dataCounts": sql("SELECT jsonb_build_object('faqs',(SELECT count(*) FROM faqs),"
                                "'embeddings',(SELECT count(*) FROM faq_embeddings),'stores',(SELECT count(*) FROM stores));"),
              "scope": "실제 API 연결 확인. 답변 품질 정확도나 환각률 측정은 아님", "cases": []}
    cases = [
        ("FAQ-STORE-GPS", "물리 유심 재발급 비용을 알려주고 현재 위치에서 유심 재발급 가능한 매장도 찾아줘",
         {"latitude": 37.501, "longitude": 127.021}, True),
        ("FAQ-STORE-FOLLOWUP", "물리 유심 재발급 비용도 알려주고 매장도 찾아줘", {}, True),
        ("FAQ-STORE-EMPTY", "물리 유심 재발급 비용을 알려주고 현재 위치에서 유심 재발급 가능한 매장도 찾아줘",
         {"latitude": 0.0, "longitude": 0.0}, False),
    ]
    if args.output.exists():
        with gzip.open(args.output, "rt", encoding="utf-8") as source:
            previous = json.load(source)
        result["previousRuns"] = previous.pop("previousRuns", []) + [previous]
    for case_id, question, coordinates, expected_stores in cases:
        opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        session = live.api(opener, args.base_url, "POST", "/api/v1/chat/sessions", {"title": case_id})
        sid = session["sessionId"]
        row = {"id": case_id, "sessionId": sid, "turns": [], "issues": []}
        result["cases"].append(row)
        content, current_coordinates = question, coordinates
        started = time.monotonic()
        try:
            for step in range(4):
                request = {"content": content, **current_coordinates}
                sent = live.api(opener, args.base_url, "POST", f"/api/v1/chat/sessions/{sid}/messages", request)
                eid = sent["executionId"]
                path = f"/api/v1/chat/sessions/{sid}/executions/{eid}"
                events, errors = [], []
                listener = threading.Thread(target=live.subscribe,
                                            args=(opener, args.base_url + path + "/subscribe", events, errors), daemon=True)
                listener.start()
                deadline = time.monotonic() + 210
                while True:
                    trace = live.api(opener, args.base_url, "GET", path + "/trace")
                    if trace["status"] != "RUNNING":
                        break
                    if time.monotonic() > deadline:
                        raise TimeoutError("실행이 완료되지 않았습니다.")
                    time.sleep(0.3)
                listener.join(timeout=10)
                history = live.api(opener, args.base_url, "GET", f"/api/v1/chat/sessions/{sid}/messages?size=50")
                assistant = [item for item in history["messages"] if item["role"] == "ASSISTANT"][-1]
                row["turns"].append({"request": request, "trace": trace, "events": events,
                                     "streamErrors": errors, "output": assistant})
                if trace["status"] != "COMPLETED":
                    raise RuntimeError("실행 실패: " + str(trace.get("errorCode")))
                if assistant["messageType"] != "CLARIFICATION":
                    break
                pending = sql(f"SELECT c.condition_key FROM consult_conditions c JOIN consult_requests r"
                              f" ON r.consult_request_id=c.consult_request_id WHERE r.session_id={int(sid)}"
                              f" AND c.status='PENDING' AND c.asked_message_id={int(assistant['messageId'])};")
                if pending == "location":
                    content, current_coordinates = "현재 위치로 찾아줘", {"latitude": 37.501, "longitude": 127.021}
                elif pending == "serviceType":
                    content, current_coordinates = "유심 재발급", current_coordinates
                else:
                    raise RuntimeError("예상하지 않은 대기 조건: " + pending)
            row["database"] = live.database_snapshot(args.database, sid, args.postgres_container)
            row["history"] = history
            row["sources"] = live.api(opener, args.base_url, "GET",
                                      f"/api/v1/chat/messages/{assistant['messageId']}/sources")
            consultations = row["database"]["consultations"]
            if (len(consultations) != 2 or any(item["status"] != "DONE" for item in consultations)
                    or sorted(item["intent"] for item in consultations) != ["FAQ", "STORE"]):
                row["issues"].append("FAQ와 매장 상담 두 건이 함께 완료되지 않음")
            if assistant["messageType"] != "ANSWER":
                row["issues"].append("최종 통합 답변이 없음")
            if bool(assistant.get("storeResults")) != expected_stores:
                row["issues"].append("매장 결과 유무 불일치")
            tokens = "".join(event["data"] for event in events if event["event"] == "token")
            if tokens != assistant.get("content"):
                row["issues"].append("SSE와 저장 답변 불일치")
            completed = [json.loads(event["data"]) for event in events if event["event"] == "complete"]
            if (not completed or completed[-1].get("outputMessage", {}).get("messageId") != assistant["messageId"]
                    or listener.is_alive() or errors):
                row["issues"].append("SSE 완료 메시지 또는 연결 종료 불일치")
            if not row["sources"].get("sources"):
                row["issues"].append("FAQ 근거가 결합 답변에 연결되지 않음")
        except Exception as error:
            row["issues"].append(f"{type(error).__name__}: {error}")
        row["elapsedSeconds"] = round(time.monotonic() - started, 3)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with gzip.open(args.output, "wt", encoding="utf-8") as output:
            json.dump(result, output, ensure_ascii=False)
        print(case_id, row["issues"] or "PASS", flush=True)
    return int(any(row["issues"] for row in result["cases"]))


if __name__ == "__main__":
    raise SystemExit(main())
