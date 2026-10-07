#!/usr/bin/env python3

"""추천 질문 실제 채팅 경로 확인: 실행 중인 앱에 질문을 보내 답변·근거·추천 질문을 모아 검토표로 쓴다

추천 질문 플래그를 켜고 실제 LLM(ollama)으로 띄운 로컬 앱이 필요하다. docs/FOLLOWUP_RECOMMENDATION.md 7.1절
  - sample: 실제 말투 질문 20개. 생성 답변과 추천의 중복·적합성을 사람이 검토표에서 판정한다
  - representatives: 대표 질문 32개를 보내 그 FAQ가 검색 1위로 나오는지 본다(버튼을 누르면 답이 나오는지)

질문마다 새 채팅방을 만들어 이전 대화가 섞이지 않게 한다. 비회원 세션 쿠키로 호출한다.
"""

from __future__ import annotations

import argparse
import csv
import http.cookiejar
import json
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "scripts/data"
POLICY_LINKS = ROOT / "src/main/resources/suggested-question/policy-links.json"
EXPECTED = ROOT / "src/test/resources/suggested-question/expected-recommendations.json"

# 검색을 통과하고 추천이 붙는 질문 16개(카테고리 10종, 질문 유형·말투 섞음)
# + 추천이 없어야 하는 질문 4개(연결 없는 정책, 경계 무관, 완전 무관 2)
# EVAL-121, EVAL-122는 답변 판정기 프롬프트 예시와 같아 쓰지 않는다
SAMPLE_IDS = [
    "EVAL-003", "EVAL-004", "EVAL-006", "EVAL-048", "EVAL-010", "EVAL-146", "EVAL-015", "EVAL-134",
    "EVAL-020", "EVAL-058", "EVAL-021", "EVAL-024", "EVAL-030", "EVAL-140", "EVAL-036", "EVAL-040",
    "EVAL-027", "EVAL-163", "EVAL-081", "EVAL-090",
]
FINAL_STATUSES = {"COMPLETED", "FAILED", "TIMEOUT", "CANCELLED"}


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise SystemExit(f"{path}: 파일을 읽을 수 없습니다: {exc}") from None


class Client:
    def __init__(self, base_url: str, timeout: float):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def call(self, method: str, path: str, body: dict | None = None):
        data = None if body is None else json.dumps(body).encode()
        req = urllib.request.Request(self.base_url + path, data=data, method=method,
                                     headers={"Content-Type": "application/json"})
        try:
            with self.opener.open(req, timeout=30) as res:
                return json.load(res)["result"]
        except urllib.error.HTTPError as exc:
            raise SystemExit(f"{method} {path}: HTTP {exc.code} {exc.read().decode(errors='replace')}") from None
        except urllib.error.URLError as exc:
            raise SystemExit(f"{self.base_url}: 앱에 연결할 수 없습니다: {exc.reason}") from None

    def ask(self, title: str, question: str) -> dict:
        session = self.call("POST", "/api/v1/chat/sessions", {"title": title[:100]})["sessionId"]
        sent = self.call("POST", f"/api/v1/chat/sessions/{session}/messages", {"content": question})
        deadline = time.monotonic() + self.timeout
        while time.monotonic() < deadline:
            messages = self.call("GET", f"/api/v1/chat/sessions/{session}/messages?size=10")["messages"]
            reply = next((m for m in messages if m["replyToMessageId"] == sent["messageId"]
                          and m["role"] == "ASSISTANT" and m["status"] in FINAL_STATUSES), None)
            if reply:
                sources = self.call("GET", f"/api/v1/chat/messages/{reply['messageId']}/sources")["sources"]
                reply["sources"] = sorted(sources, key=lambda s: s["searchRank"] or 99)
                return reply
            time.sleep(2)
        return {"status": "WAIT_TIMEOUT", "messageType": "", "content": "", "answerBasis": None,
                "followUps": [], "sources": []}


def first_source(reply: dict, slot_by_question: dict[str, str]) -> tuple[str, str]:
    if not reply["sources"]:
        return "", ""
    title = reply["sources"][0]["title"] or ""
    return slot_by_question.get(title, "(원본 외 FAQ)"), title


def run_sample(client: Client, out: Path, slot_by_question: dict[str, str], expected: dict) -> None:
    evals = {e["eval_id"]: e for e in load_json(DATA / "eval_questions_130.json")
             + load_json(DATA / "eval_questions_supplement_50.json")}
    rows, mismatch, with_chips = [], 0, 0
    for i, eval_id in enumerate(SAMPLE_IDS, 1):
        e = evals[eval_id]
        reply = client.ask(eval_id, e["question"])
        slot, title = first_source(reply, slot_by_question)
        follow_ups = reply.get("followUps") or []
        # 앱 결과가 검증한 기대 추천과 같은지(같은 1위 FAQ 기준). GROUNDED가 아니면 추천이 없어야 한다.
        # 검색 근거가 없는 답변(의도 모름 고정 버튼, 매장 찾기)은 이 기능이 관여하지 않아 "-"로 둔다
        if reply["sources"]:
            want = expected.get(slot, {}).get("suggestions", []) if reply.get("answerBasis") == "GROUNDED" else []
            same = "Y" if follow_ups == want else "N"
        else:
            same = "-"
        mismatch += same == "N"
        with_chips += bool(follow_ups) and same != "-"
        rows.append([eval_id, e["type"], e["question"], reply.get("status"), reply.get("answerBasis"),
                     slot, title, reply.get("content", ""), " | ".join(follow_ups), same, "", "", ""])
        print(f"[{i}/{len(SAMPLE_IDS)}] {eval_id} {reply.get('answerBasis')} 추천 {len(follow_ups)}개 규칙일치={same}")
    with out.open("w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["id", "유형", "질문", "상태", "답변 근거", "1위 FAQ", "1위 FAQ 질문", "생성 답변", "추천 질문",
                    "규칙 일치", "답변과 중복(Y/N)", "상황에 맞음(Y/N)", "메모"])
        w.writerows(rows)
    print(f"\n추천 질문이 붙은 답변 {with_chips}/{len(rows)}, 규칙 불일치 {mismatch}건 → {out}")
    print("검토표의 '답변과 중복', '상황에 맞음' 열을 채운다. 추천이 없어야 하는 마지막 4개도 확인한다")


def run_representatives(client: Client, out: Path, slot_by_question: dict[str, str]) -> None:
    reps = load_json(POLICY_LINKS)["representativeQuestions"]
    rows, ok = [], 0
    for i, (policy, rep) in enumerate(sorted(reps.items()), 1):
        reply = client.ask(policy, rep["question"])
        slot, _ = first_source(reply, slot_by_question)
        passed = reply.get("answerBasis") == "GROUNDED" and slot == rep["slotId"]
        ok += passed
        rows.append([policy, rep["slotId"], rep["question"], reply.get("answerBasis"), slot,
                     "Y" if passed else "N", reply.get("content", "")])
        print(f"[{i}/{len(reps)}] {policy} {reply.get('answerBasis')} 1위 {slot or '-'} {'OK' if passed else '확인 필요'}")
    with out.open("w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["정책", "대표 FAQ", "대표 질문", "답변 근거", "실제 1위 FAQ", "통과", "생성 답변"])
        w.writerows(rows)
    print(f"\n대표 질문 {ok}/{len(rows)}개가 GROUNDED + 자기 FAQ 1위 → {out}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("mode", choices=["sample", "representatives"])
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--out", type=Path, help="검토표 CSV (기본: build/suggested_questions_<mode>.csv)")
    parser.add_argument("--timeout", type=float, default=180, help="질문 하나당 답변 대기 시간(초)")
    args = parser.parse_args()

    out = args.out or ROOT / f"build/suggested_questions_{args.mode}.csv"
    out.parent.mkdir(parents=True, exist_ok=True)
    slot_by_question = {x["question"]: x["slot_id"] for x in load_json(DATA / "faq_full_1150.json")}
    client = Client(args.base_url, args.timeout)
    if args.mode == "sample":
        run_sample(client, out, slot_by_question, load_json(EXPECTED))
    else:
        run_representatives(client, out, slot_by_question)


if __name__ == "__main__":
    main()
