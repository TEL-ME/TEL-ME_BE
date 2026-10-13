"""평가셋을 실제 Spring 채팅 API로 보내 앱이 남긴 검색 trace와 답변 근거를 모은다.

점수 계산이나 검색 흉내는 하지 않는다. 라우팅, 검색, 재검색 문장 의미 검증, 답변 생성은 모두 앱이 수행한다.
앱은 LLM_PROVIDER=ollama로 띄우고, 문항마다 새 채팅 세션을 쓴다. 끊긴 실행은 같은 출력 경로로 다시 실행하면 이어 간다.

    python scripts/single-faq-search-evaluation/run_chat_eval.py --base-url http://localhost:8101 \
        --database telme_eval146_fix --output .measure/telme146/chat-fix.json
    python scripts/single-faq-search-evaluation/run_chat_eval.py --compare .measure/telme146/chat-dev.json \
        .measure/telme146/chat-fix.json
"""
import argparse
import http.cookiejar
import json
import statistics
import subprocess
import time
import urllib.request
from pathlib import Path

EVAL_SET = Path(__file__).resolve().parents[1] / "data/eval_questions_180_multigold.json"


def client(base):
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(method, path, body=None):
        request = urllib.request.Request(base + path, method=method,
                                         data=None if body is None else json.dumps(body).encode("utf-8"),
                                         headers={"Content-Type": "application/json; charset=UTF-8"})
        try:
            return json.load(opener.open(request, timeout=240))
        except urllib.error.HTTPError as error:
            return json.loads(error.read().decode("utf-8", "replace") or "{}")
    return call


def run(args):
    def sql(query):
        return subprocess.run(["docker", "exec", args.container, "psql", "-U", "telme", "-d", args.database,
                               "-Atc", query], capture_output=True, text=True, encoding="utf-8").stdout.strip()

    items = json.loads(EVAL_SET.read_text(encoding="utf-8"))
    rows = json.loads(args.output.read_text(encoding="utf-8")) if args.output.exists() else []
    done = {row["id"] for row in rows}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    for number, item in enumerate(items, 1):
        if item["eval_id"] in done:
            continue
        call = client(args.base_url)
        session_id = call("POST", "/api/v1/chat/sessions", {"title": "search-eval"})["result"]["sessionId"]
        started = time.time()
        sent = call("POST", f"/api/v1/chat/sessions/{session_id}/messages", {"content": item["question"]})
        history = {"messages": []}
        while time.time() - started < 240:
            history = call("GET", f"/api/v1/chat/sessions/{session_id}/messages?size=10")["result"]
            if history.get("runningExecutionId") is None:
                break
            time.sleep(0.4)
        elapsed = round(time.time() - started, 2)
        reply = ([m for m in history["messages"] if m["role"] == "ASSISTANT"] or [{}])[-1]
        message_id = (sent.get("result") or {}).get("messageId")
        trace = json.loads(sql(f"SELECT coalesce(pipeline_trace::text,'{{}}') FROM chat_executions "
                               f"WHERE input_message_id={message_id}") or "{}") if message_id else {}
        routing = sql(f"SELECT coalesce(intent,'-')||'|'||coalesce(method,'-')||'|'||coalesce(refined_query,'') "
                      f"FROM query_routings WHERE message_id={message_id}") if message_id else ""
        searches = [{"kind": s.get("kind"), "query": s.get("query"), "status": s.get("status"),
                     "slots": [source.get("slotId") for source in s.get("sources") or []]}
                    for s in trace.get("searchResults") or []]
        rows.append({"id": item["eval_id"], "type": item["type"], "question": item["question"],
                     "gold": sorted(item.get("expected_slot_id") or []), "routing": routing,
                     "searches": searches, "refinedChecks": trace.get("refinedQueryChecks") or [],
                     "messageType": reply.get("messageType"), "answerBasis": reply.get("answerBasis"),
                     "content": reply.get("content"), "elapsed": elapsed})
        print(f"{number:3} {elapsed:6}s {reply.get('answerBasis')!s:<12} "
              f"{[s['kind'] + ':' + s['status'] for s in searches]}", flush=True)
        args.output.write_text(json.dumps(rows, ensure_ascii=False, indent=1), encoding="utf-8")


def summary(rows):
    answerable = [r for r in rows if r["gold"]]
    unrelated = [r for r in rows if not r["gold"]]

    def found(r):
        return {slot for s in r["searches"] if s["status"] == "FOUND" for slot in s["slots"]}

    def refined(r):
        return any(s["kind"] == "REFINED" for s in r["searches"])

    def first_status(r, status):
        return [r["elapsed"] for r in rows if r["searches"] and r["searches"][0]["status"] == status]

    return {
        "답변 가능: 정답 근거 검색": sum(bool(found(r) & set(r["gold"])) for r in answerable),
        "답변 가능: GROUNDED 답변": sum(r["answerBasis"] == "GROUNDED" for r in answerable),
        "답변 가능: 재검색 실행": sum(refined(r) for r in answerable),
        "무관: 검색 결과 있음": sum(bool(found(r)) for r in unrelated),
        "무관: GROUNDED 답변": sum(r["answerBasis"] == "GROUNDED" for r in unrelated),
        "무관: 재검색 실행": sum(refined(r) for r in unrelated),
        "재검색 직전 의미 검증": sum(len(r["refinedChecks"]) for r in rows),
        "처리 시간 중앙값(초)": round(statistics.median(r["elapsed"] for r in rows), 2),
        "원문 검색 성공 시 중앙값(초)": round(statistics.median(first_status(rows, "FOUND") or [0]), 2),
        "원문 검색 0건 시 중앙값(초)": round(statistics.median(first_status(rows, "EMPTY") or [0]), 2),
    }


def compare(before_path, after_path):
    before = {r["id"]: r for r in json.loads(Path(before_path).read_text(encoding="utf-8"))}
    after = {r["id"]: r for r in json.loads(Path(after_path).read_text(encoding="utf-8"))}
    common = sorted(set(before) & set(after))
    a, b = summary([before[i] for i in common]), summary([after[i] for i in common])
    print(f"공통 문항 {len(common)}건")
    for key in a:
        print(f"{key}: {a[key]} -> {b[key]}")
    print("\n답변 근거가 달라진 문항")
    for i in common:
        if before[i]["answerBasis"] != after[i]["answerBasis"]:
            r = after[i]
            print(f"- {i} [{'무관' if not r['gold'] else '답변 가능'}] {before[i]['answerBasis']} -> "
                  f"{r['answerBasis']} {r['question']} | {[(s['kind'], s['query']) for s in r['searches']]}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url")
    parser.add_argument("--database")
    parser.add_argument("--container", default="telme-flow-postgres")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--compare", nargs=2, metavar=("BEFORE", "AFTER"))
    args = parser.parse_args()
    if args.compare:
        compare(*args.compare)
        return
    if not (args.base_url and args.database and args.output):
        parser.error("--base-url, --database, --output이 필요합니다.")
    if args.database == "telme":
        parser.error("운영 개발 DB가 아닌 평가용 복사본 DB를 지정해야 합니다.")
    run(args)


if __name__ == "__main__":
    main()
