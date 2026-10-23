"""TELME-225 채점: 캡처에서 턴마다 라우팅 하위 질문(의도, 검색어 띄어쓰기), 답하지 못한 부분, 매장 업무를 낸다.

usage: python scripts/spacing-routing-evaluation/score_compound_capture.py <run_dir>... --out <json>
run_dir은 run_chat_pipeline_eval --capture-only의 결과 폴더(capture.json이 있는 곳)다.
매장 업무 조건은 run_dir 이름으로 만든 평가 DB(telme_judge_<run_dir의 - 를 _로>)에서 읽는다.
"""
import argparse
import json
import re
import subprocess
from pathlib import Path

NO_ANSWER = ("정확하게 안내드리기 어려워요", "찾지 못했습니다", "나누어 처리하기 어려워요", "이해하지 못했어요",
             "통신 서비스와 매장 관련 질문을 도와드려요")


def service_types(database):
    out = subprocess.run(["docker", "exec", "telme-flow-postgres", "psql", "-U", "telme", "-d", database, "-AtF", "\t",
                          "-c", "SELECT r.origin_message_id, c.status, coalesce(c.condition_value,'') FROM consult_requests r "
                                "JOIN consult_conditions c USING (consult_request_id) "
                                "WHERE r.intent='STORE' AND c.condition_key='serviceType'"],
                         capture_output=True).stdout.decode("utf-8")
    result = {}
    for line in out.splitlines():
        origin, status, value = line.split("\t")
        result[int(origin)] = value if status == "FILLED" else None
    return result


def parts(content):
    # 복합 답은 "1. (질문) 답 2. (질문) 답" 꼴이다. 번호가 없으면 한 덩어리로 본다
    pieces = re.split(r"(?:^|\n|\s)(?=\d\.\s)", content or "")
    return [piece for piece in pieces if piece.strip()] or [content or ""]


def score_turn(case_id, turn, services):
    fixture = turn["fixture"]
    exchanges = turn.get("clarificationExchanges") or []
    first = exchanges[0] if exchanges else turn
    final = turn.get("outputMessage") or {}
    requests = first.get("consultRequests") or []
    faq_queries = [r["query_text"] or "" for r in requests if r["intent"] == "FAQ"]
    content = final.get("content") or ""
    unanswered = [piece[:60] for piece in parts(content) if any(marker in piece for marker in NO_ANSWER)]
    asked = [key for exchange in exchanges for key in exchange.get("askedKeys", [])]
    route = first.get("route") or {}
    return {"caseId": case_id, "group": fixture.get("group"), "question": fixture["question"],
            "expectedBehavior": fixture.get("expectedBehavior"), "expectedServiceType": fixture.get("expectedServiceType"),
            "executionStatus": turn.get("executionStatus"), "routeIntent": route.get("intent"),
            "intents": [r["intent"] for r in requests], "faqQueries": faq_queries,
            "spacelessFaqQueries": [q for q in faq_queries if len(q) > 8 and not re.search(r"\s", q)],
            "finalType": final.get("messageType"), "finalContent": content[:200], "unansweredParts": unanswered,
            "partCount": len(parts(content)), "askedKeys": asked, "storeResults": bool(final.get("storeResults")),
            "serviceType": services.get(first.get("inputMessageId"))}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("runs", nargs="+", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    result = {}
    for run in args.runs:
        capture = json.loads((run / "capture.json").read_text(encoding="utf-8"))
        services = service_types("telme_judge_" + run.name.replace("-", "_"))
        rows = [score_turn(case["caseId"], turn, services) for case in capture["cases"] for turn in case["turns"]]
        result[run.name] = {"complete": capture.get("captureComplete"), "rows": rows}
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
