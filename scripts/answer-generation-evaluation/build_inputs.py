"""기존 평가셋을 답변 생성 직전 입력(A: 정답 근거 고정)으로 바꾼다. 모델을 부르지 않는다.

앞단(문맥 판정, 라우팅, 되묻기)과 검색을 거치지 않고, 평가셋이 가리키는 FAQ를 근거로 그대로 붙인다.
앞단이 완벽한 상황에서 답변 생성만 비교하려는 입력이다. 질문 중 표준 비교 표현은 실제 앱에서 비교 근거 검증이
답을 만들기 때문에 뺀다.

사용: python3 -I scripts/answer-generation-evaluation/build_inputs.py [출력 경로]
"""

import json
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "scripts" / "data"
MAX_EVIDENCE = 3  # 앱의 기본 검색(top-3)과 같은 개수
COMPARISON = re.compile(r"비교|차이")


def load(name):
    return json.loads((DATA / name).read_text(encoding="utf-8"))


def evidence(faqs, slots):
    result = []
    for rank, slot in enumerate(slots[:MAX_EVIDENCE], start=1):
        faq = faqs[slot]
        result.append({"faqId": rank, "slotId": slot, "category": faq["category"], "question": faq["question"],
                       "answer": faq["answer"], "score": 1.0})
    return result


def as_list(value):
    return [value] if isinstance(value, str) else list(value or [])


def main():
    faqs = {row["slot_id"]: row for row in load("faq_full_1150.json")}
    inputs, skipped = [], Counter()

    def add(set_name, row, slots, meta):
        if COMPARISON.search(row["question"]):
            skipped["비교 표현"] += 1
            return
        missing = [slot for slot in slots if slot not in faqs]
        if missing or not slots:
            skipped["근거 FAQ 없음"] += 1
            return
        inputs.append({"id": row["eval_id"], "userQuery": row["question"],
                       "searchResults": evidence(faqs, slots),
                       "meta": {"set": set_name, "goldSlotIds": as_list(slots), **meta}})

    for row in load("eval_questions_180_multigold.json"):
        if row["type"] == "UNRELATED":
            skipped["무관(근거 없음)"] += 1
            continue
        add("eval180", row, as_list(row["expected_slot_id"]), {"type": row["type"]})
    for row in load("eval_answer_quality_20.json"):
        add("answer_quality_20", row, as_list(row["expected_slot_id"]), {"type": row["type"], "risk": row["risk"]})
    for row in load("eval_questions_holdout_90.json"):
        add("holdout90", row, as_list(row["related_slot_id"]),
            {"type": "ADJACENT_HARD", "expectNoAnswer": True, "note": row.get("note")})

    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "scripts" / "answer-generation-evaluation" / "inputs-a.json"
    out.write_text(json.dumps(inputs, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"입력 {len(inputs)}개 → {out}")
    print("출처별:", dict(Counter(item["meta"]["set"] for item in inputs)))
    print("뺀 것:", dict(skipped))


if __name__ == "__main__":
    main()
