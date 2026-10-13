"""별도 평가 DB와 실제 Spring API로 FAQ+매장 16문항의 최초 처리를 확인한다."""

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
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path


QUESTIONS = [
    ("명의변경 서류 알려주고 가까운 매장도 찾아줘", False),
    ("유심 재발급 비용 알려주고 근처 유심 재발급 매장도 찾아줘", True),
    ("번호이동 방법이랑 번호이동 되는 매장 알려줘", False),
    ("로밍 신청 방법 알려주고 강남역 근처 매장도 알려줘", False),
    ("요금제 변경은 어떻게 해? 그리고 신촌역 매장 위치도 알려줘", False),
    ("분실 신고 방법이랑 홍대입구역 근처 매장 찾아줘", False),
    ("eSIM 개통 절차 알려주고 현재 위치에서 가까운 매장도 찾아줘", True),
    ("인터넷 해지할 때 장비 반납 방법이랑 근처 매장 알려줘", False),
    ("휴대폰 일시정지 신청 방법 알려주고 잠실역 매장도 찾아줘", False),
    ("명의변경 수수료 얼마야? 그리고 가까운 명의변경 매장도", True),
    ("신규 개통 준비물 알려주고 판교역 근처 개통 가능한 매장 찾아줘", False),
    ("유심 재발급 방법이랑 서울역 근처 유심 재발급 매장 알려줘", False),
    ("소액결제 차단 방법 알려주고 가까운 매장도 알려줘", True),
    ("데이터 쉐어링 신청 방법이랑 여의도역 근처 매장 위치 알려줘", False),
    ("번호이동 하면 위약금 있나요? 그리고 근처 번호이동 매장 찾아주세요", True),
    ("가족결합 할인 조건 알려주고 강남역 매장도 찾아줘", False),
]


def sse_issues(row):
    answer = row["output"]
    events = row["sseEvents"]
    complete = [json.loads(e["data"]) for e in events if e["event"] == "complete"]
    issues = []
    # 완료 이벤트는 본문이 아니라 저장 메시지의 식별자를 보낸다.
    if not complete or complete[-1].get("outputMessage", {}).get("messageId") != answer["messageId"]:
        issues.append("SSE 완료 응답과 저장 메시지 ID 불일치")
    tokens = "".join(e["data"] for e in events if e["event"] == "token")
    # 확인 안내와 되묻기는 토큰 없이 완료 이벤트로 전달될 수 있다.
    if tokens and tokens != (answer.get("content") or ""):
        issues.append("SSE 토큰과 저장 본문 불일치")
    if row["sseErrors"]:
        issues.append("SSE 종료 오류")
    return issues


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database", required=True)
    parser.add_argument("--base-url", default="http://localhost:18135")
    parser.add_argument("--llm-log", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--baseline", type=Path)
    args = parser.parse_args()
    if not args.database.startswith("telme_135_"):
        parser.error("별도 telme_135_ 평가 DB가 필요합니다.")
    if args.output.exists():
        parser.error("기존 결과를 덮어쓰지 않습니다.")
    repo = Path(__file__).resolve().parents[2]
    spec = importlib.util.spec_from_file_location("live", repo / "scripts/compound-faq-evaluation/run_live_api.py")
    live = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(live)

    def sql(query):
        process = subprocess.run(["docker", "exec", "-i", "telme-flow-postgres", "psql", "-U", "telme",
                                  "-d", args.database, "-At", "-v", "ON_ERROR_STOP=1"],
                                 input=query, encoding="utf-8", capture_output=True, check=True, timeout=20)
        return process.stdout.strip()

    # 운영체제와 무관하게 같은 값이 나오도록 경로는 POSIX 문자열 순서로, 줄바꿈은 LF로 맞춘다.
    digest = hashlib.sha256()
    for name in sorted(path.relative_to(repo).as_posix() for path in (repo / "src/main/java").rglob("*.java")):
        digest.update(name.encode())
        digest.update((repo / name).read_bytes().replace(b"\r\n", b"\n"))
    result = {"gitHead": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip(),
              "sourceTreeSha256": digest.hexdigest(), "database": args.database, "baseUrl": args.base_url,
              "recordedAt": datetime.now(timezone.utc).isoformat(),
              "cases": []}
    # 기대값은 모델 실행 전에 고정한다. 매장 조건 미충족에 따른 정상 되묻기도 요청 보존 성공이다.
    result["expectation"] = "16문항 모두 FAQ 한 건과 STORE 한 건의 BOTH. 최종 답변 품질은 채점하지 않음."
    with urllib.request.urlopen("http://localhost:11434/api/tags", timeout=15) as response:
        result["models"] = json.load(response)["models"]
    result["dataCounts"] = json.loads(sql("SELECT jsonb_build_object('faqs',(SELECT count(*) FROM faqs),"
                                         "'embeddings',(SELECT count(*) FROM faq_embeddings),"
                                         "'stores',(SELECT count(*) FROM stores));"))
    if args.baseline:
        result["previousRun"] = {"origin": "클로드의 임시 compound_rate.json. 원시 모델 출력은 없음.",
                                 "cases": json.loads(args.baseline.read_text(encoding="utf-8"))}
    args.output.parent.mkdir(parents=True, exist_ok=True)

    def save():
        if args.llm_log.exists():
            result["modelCalls"] = [json.loads(line) for line in args.llm_log.read_text(encoding="utf-8").splitlines()]
        with gzip.open(args.output, "wt", encoding="utf-8") as output:
            json.dump(result, output, ensure_ascii=False)

    for index, (question, gps) in enumerate(QUESTIONS, 1):
        opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        row = {"id": f"MIXED-{index:02d}", "question": question, "gps": gps,
               "expectedIntent": "BOTH", "expectedSubIntents": ["FAQ", "STORE"],
               "startedAt": datetime.now(timezone.utc).isoformat(), "issues": []}
        result["cases"].append(row)
        started = time.monotonic()
        try:
            sid = live.api(opener, args.base_url, "POST", "/api/v1/chat/sessions", {"title": row["id"]})["sessionId"]
            row["sessionId"] = sid
            body = {"content": question}
            if gps:
                body.update(latitude=37.4979, longitude=127.0276)
            row["request"] = body
            sent = live.api(opener, args.base_url, "POST", f"/api/v1/chat/sessions/{sid}/messages", body)
            row["sent"] = sent
            path = f"/api/v1/chat/sessions/{sid}/executions/{sent['executionId']}"
            events, errors = [], []
            listener = threading.Thread(target=live.subscribe, args=(opener, args.base_url + path + "/subscribe",
                                                                     events, errors), daemon=True)
            listener.start()
            deadline = time.monotonic() + 210
            while True:
                trace = live.api(opener, args.base_url, "GET", path + "/trace")
                if trace["status"] != "RUNNING":
                    break
                if time.monotonic() > deadline:
                    raise TimeoutError("채팅 실행이 완료되지 않았습니다.")
                time.sleep(0.3)
            listener.join(timeout=10)
            row.update(trace=trace, sseEvents=events, sseErrors=errors)
            history = live.api(opener, args.base_url, "GET", f"/api/v1/chat/sessions/{sid}/messages?size=50")
            row["history"] = history
            answer = [m for m in history["messages"] if m["role"] == "ASSISTANT"][-1]
            row["output"] = answer
            row["database"] = live.database_snapshot(args.database, sid, "telme-flow-postgres")
            row["routing"] = json.loads(sql(f"SELECT COALESCE((SELECT to_jsonb(q) FROM query_routings q"
                                            f" WHERE q.message_id={int(sent['messageId'])}),'null'::jsonb);"))
            consultations = row["database"]["consultations"]
            row["requestsPreserved"] = (len(consultations) == 2 and
                                         sorted(x["intent"] for x in consultations) == ["FAQ", "STORE"])
            text = answer.get("content") or ""
            row["outcome"] = ("복합 요청 보존" if row["requestsPreserved"] else
                              "분리 요청 안내" if "하나씩" in text or "나누어" in text else "한 요청만 처리")
            if trace["status"] != "COMPLETED":
                row["issues"].append("채팅 실행 실패")
            row["issues"].extend(sse_issues(row))
            if listener.is_alive():
                row["issues"].append("SSE 종료 오류")
        except Exception as error:
            row["issues"].append(f"{type(error).__name__}: {error}")
        row["elapsedSeconds"] = round(time.monotonic() - started, 3)
        row["completedAt"] = datetime.now(timezone.utc).isoformat()
        save()
        print(row["id"], row.get("outcome"), row["issues"], flush=True)
    result["summary"] = dict(Counter(x.get("outcome", "실행 오류") for x in result["cases"]))
    result["captureComplete"] = True
    save()
    print(result["summary"], flush=True)


if __name__ == "__main__":
    main()
