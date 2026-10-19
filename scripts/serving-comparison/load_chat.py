"""채팅 API에 질문을 동시 대화 N개로 보내 턴 응답 시간과 실패를 잰다(서빙 비교 T1, README 참고).

질문은 서비스 품질셋 v2.2의 단일 턴 질문에서 고정 순서로 고른다(같은 --questions면 매번 같은 질문). 질문마다 새 게스트
세션을 만들어 앞 대화의 영향을 없앤다. 턴 시간은 메시지를 보낸 뒤 그 질문의 답변이 저장될 때까지다(되물으면 되묻기까지).

사용:
  python load_chat.py --base-url http://localhost:8080 --concurrency 5 --label vllm-S1-N5 --output results/vllm-S1-N5.jsonl
  python load_chat.py --summarize results/*.jsonl      # 저장된 결과를 표로
"""
import argparse
import http.cookiejar
import json
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_SET = ROOT / "scripts/chat_judge/data/chat_pipeline_quality_v2_2.json"


def client(base, timeout):
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(method, path, body=None):
        request = urllib.request.Request(base + path, method=method,
                                         data=None if body is None else json.dumps(body).encode("utf-8"),
                                         headers={"Content-Type": "application/json; charset=UTF-8"})
        try:
            return json.load(opener.open(request, timeout=timeout))
        except urllib.error.HTTPError as error:
            return json.loads(error.read().decode("utf-8", "replace") or "{}")
    return call


def pick_questions(path, count):
    # 묶음(category)을 돌아가며 하나씩 골라 FAQ·복합·답 없음이 고르게 섞이게 한다
    cases = [c for c in json.loads(Path(path).read_text(encoding="utf-8")) if len(c["turns"]) == 1]
    groups = {}
    for case in cases:
        groups.setdefault(case["category"], []).append(case)
    picked = []
    while len(picked) < count and any(groups.values()):
        for name in sorted(groups):
            if groups[name] and len(picked) < count:
                picked.append(groups[name].pop(0))
    return [(c["caseId"], c["category"], c["turns"][0]["question"]) for c in picked]


def run_one(base, case, timeout, poll):
    case_id, category, question = case
    call = client(base, timeout)
    row = {"caseId": case_id, "category": category, "question": question}
    try:
        session_id = call("POST", "/api/v1/chat/sessions", {"title": "serving-compare"})["result"]["sessionId"]
        started = time.time()
        sent = call("POST", f"/api/v1/chat/sessions/{session_id}/messages", {"content": question})
        user_id = (sent.get("result") or {}).get("messageId")
        if user_id is None:
            return {**row, "status": "SEND_FAILED", "error": json.dumps(sent, ensure_ascii=False)[:300]}
        while time.time() - started < timeout:
            history = call("GET", f"/api/v1/chat/sessions/{session_id}/messages?size=20")["result"]
            replies = [m for m in history.get("messages", [])
                       if m["role"] == "ASSISTANT" and m.get("replyToMessageId") == user_id]
            if history.get("runningExecutionId") is None and replies and all(
                    (m.get("content") or "").strip() or m.get("storeResults") for m in replies):
                return {**row, "status": "OK", "seconds": round(time.time() - started, 2), "sessionId": session_id,
                        "types": [m.get("messageType") for m in replies],
                        "basis": [m.get("answerBasis") for m in replies],
                        "chars": sum(len(m.get("content") or "") for m in replies),
                        # 정확도 비교(T4)에서 Judge로 채점할 수 있게 답 내용도 남긴다
                        "content": "\n---\n".join(m.get("content") or "" for m in replies)}
            time.sleep(poll)
        return {**row, "status": "TIMEOUT", "seconds": timeout, "sessionId": session_id}
    except Exception as error:  # 측정은 계속한다. 실패 원인은 결과에 남긴다
        return {**row, "status": "ERROR", "error": f"{type(error).__name__}: {error}"[:300]}


def percentile(values, q):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, int(round(q * (len(ordered) - 1))))] if ordered else None


def summary_line(label, rows, wall=None):
    ok = [r["seconds"] for r in rows if r["status"] == "OK"]
    bad = {s: sum(1 for r in rows if r["status"] == s) for s in ("TIMEOUT", "ERROR", "SEND_FAILED")}
    no_evidence = sum(1 for r in rows if r["status"] == "OK" and "NO_EVIDENCE" in (r.get("basis") or []))
    parts = [f"{label:<28}", f"턴 {len(rows):>3}", f"성공 {len(ok):>3}",
             f"시간초과 {bad['TIMEOUT']}", f"오류 {bad['ERROR'] + bad['SEND_FAILED']}",
             f"근거없음 {no_evidence:>3}"]
    if ok:
        parts += [f"p50 {statistics.median(ok):6.2f}s", f"p95 {percentile(ok, 0.95):6.2f}s", f"최대 {max(ok):6.2f}s"]
    if wall:
        parts.append(f"처리량 {len(ok) / wall * 60:5.1f}턴/분")
    return " | ".join(parts)


def summarize(paths):
    for path in paths:
        rows = [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]
        meta = next((r for r in rows if "meta" in r), {}).get("meta", {})
        print(summary_line(meta.get("label", Path(path).stem), [r for r in rows if "meta" not in r], meta.get("wall")))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--set", default=str(DEFAULT_SET), help="질문을 고를 평가셋")
    parser.add_argument("--questions", type=int, default=100)
    parser.add_argument("--concurrency", type=int, default=1, help="동시에 진행하는 대화 수 N")
    parser.add_argument("--timeout", type=float, default=180, help="턴 하나를 기다리는 최대 초")
    parser.add_argument("--poll", type=float, default=0.2, help="답변 확인 간격(초)")
    parser.add_argument("--label", help="결과 이름(예: vllm-S1-N5)")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--summarize", nargs="+", help="저장된 결과 파일을 표로 출력하고 끝낸다")
    args = parser.parse_args()

    if args.summarize:
        summarize(args.summarize)
        return
    if not args.output or not args.label:
        parser.error("--label과 --output이 필요하다")

    cases = pick_questions(args.set, args.questions)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    lock = threading.Lock()
    rows = []
    started = time.time()
    with args.output.open("w", encoding="utf-8") as out, ThreadPoolExecutor(args.concurrency) as pool:
        futures = [pool.submit(run_one, args.base_url, case, args.timeout, args.poll) for case in cases]
        for future in as_completed(futures):
            row = future.result()
            with lock:
                rows.append(row)
                out.write(json.dumps(row, ensure_ascii=False) + "\n")
                out.flush()
            print(f"{len(rows):>3}/{len(cases)} {row['status']:<8} {row.get('seconds', '-')}s {row['caseId']}",
                  file=sys.stderr, flush=True)
        wall = time.time() - started
        out.write(json.dumps({"meta": {"label": args.label, "concurrency": args.concurrency,
                                       "questions": len(cases), "wall": round(wall, 1),
                                       "startedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z", time.localtime(started))}},
                             ensure_ascii=False) + "\n")
    print(summary_line(args.label, rows, wall))


if __name__ == "__main__":
    main()
