#!/usr/bin/env python3

"""FAQ 질문 답변 확인: FAQ 질문을 그대로 실행 중인 앱에 보내 근거 있는 답(GROUNDED)이 나오는지 본다

추천 버튼은 FAQ 질문 문장을 다시 보낸다. 그 문장으로 답이 안 나오는 FAQ는 버튼으로 쓰면 눌러도 "정보 없음"이 뜬다.
결과는 scripts/data/faq_unanswerable.json에 쓰고, build_suggested_question_data.py가 읽어
  - 대표 질문이 이 목록에 있으면 오류를 낸다
  - 답을 못 할 때 보여 줄 FAQ 후보에서 뺀다(faq-rules.json unanswerableSlotIds)
docs/FOLLOWUP_RECOMMENDATION.md 11.4절. 라우터·검색·답변 생성이 바뀌면 다시 돌린다.

실제 LLM(ollama)으로 띄운 로컬 앱이 필요하다(FAQ 1,150개에 약 1시간 반). 답이 안 나오면 한 번 더 보내고,
LLM 결과가 매번 같지 않으므로 한 번이라도 안 나오면 목록에 넣는다. 질문마다 새 채팅방을 쓴다.
"""

from __future__ import annotations

import argparse
import datetime
import json
from pathlib import Path

from check_suggested_questions_live import Client, load_json

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "scripts/data/faq_full_1150.json"
OUT = ROOT / "scripts/data/faq_unanswerable.json"


def answered(client: Client, question: str) -> bool:
    return client.ask(question, question).get("answerBasis") == "GROUNDED"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--timeout", type=float, default=180, help="질문 하나당 답변 대기 시간(초)")
    parser.add_argument("--limit", type=int, help="앞에서부터 이 개수만 확인(동작 확인용, 결과 파일은 쓰지 않음)")
    args = parser.parse_args()

    client = Client(args.base_url, args.timeout)
    items = sorted(load_json(SRC), key=lambda x: x["slot_id"])
    if args.limit:
        items = items[:args.limit]
    failed, failed_twice = [], []
    for i, faq in enumerate(items, 1):
        if not answered(client, faq["question"]):
            failed.append(faq["slot_id"])
            if not answered(client, faq["question"]):
                failed_twice.append(faq["slot_id"])
        if i % 50 == 0:
            print(f"{i}/{len(items)} 답이 안 나옴 {len(failed)}", flush=True)

    print(f"{len(items)}개 중 답이 안 나옴 {len(failed)}개 (두 번 다 {len(failed_twice)}개)")
    if args.limit:
        return
    OUT.write_text(json.dumps({
        "measuredOn": datetime.date.today().isoformat(),
        "method": "ACTIVE FAQ 질문을 그대로 채팅에 보내 근거 있는 답(GROUNDED)이 나오는지 본다. "
                  "안 나오면 한 번 더 보낸다. 한 번이라도 안 나오면 넣는다",
        "total": len(items),
        "failedTwice": len(failed_twice),
        "slotIds": sorted(failed),
    }, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{OUT.relative_to(ROOT)}를 썼습니다. build_suggested_question_data.py를 다시 실행하세요")


if __name__ == "__main__":
    main()
