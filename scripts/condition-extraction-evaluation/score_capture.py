"""TELME-218 되묻기 채점: 캡처에서 턴마다 FAQ 되묻기 여부, 조건 뽑기 호출(1위 FAQ, 시간, 토큰), 매장 흐름 도달을 낸다.

usage: python scripts/condition-extraction-evaluation/score_capture.py <run_dir>... --faqs <faqs.tsv> --out <json>
run_dir은 run_chat_pipeline_eval --capture-only의 결과 폴더(capture.json이 있는 곳)다.
faqs.tsv는 1위 FAQ 질문을 faq_id로 바꾸는 데 쓰고, 평가 DB에서 faq_id, category, question, answer를 탭으로 나눠 뽑는다
(질문과 답의 줄바꿈은 공백으로 바꾼다).
"""
import argparse
import json
import re
from pathlib import Path

STORE_KEYS = {"serviceType", "location"}
EXTRACTOR = "질문 분석기"
FAQ_BY_QUESTION = {}


def load_faqs(path):
    for line in path.read_text(encoding="utf-8").splitlines():
        parts = line.split("\t")
        if len(parts) >= 3:
            FAQ_BY_QUESTION.setdefault(parts[2].strip(), int(parts[0]))


def extraction_calls(step):
    calls = []
    for call in step.get("bedrockCalls") or []:
        if EXTRACTOR not in call.get("systemHead", ""):
            continue
        text = (call.get("messages") or [{}])[0].get("text", "")
        top = re.search(r"\[1\] Q\. (.*)", text)
        top_question = top.group(1).strip() if top else None
        try:
            conditions = json.loads(call.get("response") or "")["conditions"]
        except (ValueError, KeyError, TypeError):
            conditions = None
        calls.append({"topFaqId": FAQ_BY_QUESTION.get(top_question), "topQuestion": top_question,
                      "sourceCount": len(re.findall(r"^\[\d+\] Q\.", text, re.M)),
                      "elapsedMs": call.get("elapsedMs"), "inputTokens": call.get("inputTokens"),
                      "outputTokens": call.get("outputTokens"), "stopReason": call.get("stopReason"),
                      "conditions": conditions})
    return calls


def score_turn(case_id, turn):
    fixture = turn["fixture"]
    exchanges = turn.get("clarificationExchanges") or []
    steps = exchanges + [turn]
    first = steps[0]
    first_out = first.get("outputMessage") or {}
    first_keys = first.get("askedKeys") if exchanges else turn.get("unansweredClarificationKeys")
    if first_keys is None and first_out.get("messageType") == "CLARIFICATION":
        first_keys = turn.get("unansweredClarificationKeys") or []
    faq_asked = first_out.get("messageType") == "CLARIFICATION" and not set(first_keys or []) <= STORE_KEYS
    calls = [call for step in steps for call in extraction_calls(step)]
    later_keys = [key for step in exchanges[1:] for key in step.get("askedKeys") or []]
    later_keys += turn.get("unansweredClarificationKeys") or [] if exchanges else []
    final = turn.get("outputMessage") or {}
    intents = {request.get("intent") for step in steps[1:] for request in step.get("consultRequests") or []}
    store_flow = (final.get("messageType") == "STORE_RESULT" or bool(final.get("storeResults"))
                  or bool(STORE_KEYS & set(later_keys)) or "STORE" in intents or "BOTH" in intents)
    return {"caseId": case_id, "group": fixture.get("group"), "faqId": fixture.get("faqId"),
            "question": fixture["question"], "expectClarification": fixture.get("expectClarification"),
            "expectedAsk": fixture.get("expectedAsk"), "expectedBehavior": fixture.get("expectedBehavior"),
            "faqAsked": faq_asked, "firstKeys": first_keys, "firstContent": (first_out.get("content") or "")[:150],
            "firstType": first_out.get("messageType"), "finalType": final.get("messageType"),
            "finalContent": (final.get("content") or "")[:150], "storeFlow": store_flow,
            "unanswered": turn.get("unansweredClarificationKeys"), "extraction": calls,
            "executionStatus": turn.get("executionStatus")}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("runs", nargs="+", type=Path)
    parser.add_argument("--faqs", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    load_faqs(args.faqs)
    result = {}
    for run in args.runs:
        capture = json.loads((run / "capture.json").read_text(encoding="utf-8"))
        rows = [score_turn(case["caseId"], turn) for case in capture["cases"] for turn in case["turns"]]
        result[run.name] = {"complete": capture.get("captureComplete"), "rows": rows}
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
