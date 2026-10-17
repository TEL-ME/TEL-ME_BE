"""B-1: 앞단을 실제로 통과시킬 시나리오 목록(약 250개)을 기존 평가셋에서 만든다. 모델을 부르지 않는다.

시나리오는 고객 발언의 순서다. 한 시나리오의 발언을 같은 채팅 세션에 차례로 보내고, 답변 생성 직전 입력을 저장한다.
구성: 복합 FAQ 전부, FAQ+매장 전부, 되묻기 시나리오(답변 단계가 있는 것), 대화 흐름(매장 제외),
문맥 판정 평가셋과 단일 FAQ는 일부(고정 시드). 비교 표현 질문은 비교 근거 검증이 답을 만들어 뺀다.

사용: python3 -I scripts/answer-generation-evaluation/build_scenarios_b.py [출력 경로]
"""

import gzip
import json
import random
import re
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SEED = 178
COMPARISON = re.compile(r"비교|차이")
CONTEXT_SAMPLE = 60
SINGLE_SAMPLE = 40


def load(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))


def stratified(rng, groups, total):
    """묶음마다 비율대로 뽑는다. 묶음 안의 순서는 시드로 섞는다."""
    size = sum(len(items) for items in groups.values())
    picked = []
    for name in sorted(groups):
        items = list(groups[name])
        rng.shuffle(items)
        picked.extend(items[:max(1, round(total * len(items) / size))])
    rng.shuffle(picked)
    return picked[:total]


def main():
    rng = random.Random(SEED)
    scenarios, seen = [], set()

    def add(sid, group, source, turns):
        key = tuple(turns)
        if key in seen or any(COMPARISON.search(turn) for turn in turns):
            return False
        seen.add(key)
        scenarios.append({"id": sid, "group": group, "source": source, "turns": turns})
        return True

    unified = load("scripts/data/eval_routing_unified.json")
    items = unified["items"]
    for item in items:
        count = item["expected_request_count"] or 0
        if item["expected_intent"] == "BOTH":
            add(item["eval_id"], "FAQ+매장", item["source"], [item["question"]])
        elif item["expected_intent"] == "FAQ" and count >= 2:
            add(item["eval_id"], "복합 FAQ", item["source"], [item["question"]])
    singles = {}
    for item in items:
        if item["expected_intent"] == "FAQ" and (item["expected_request_count"] or 0) == 1 \
                and not COMPARISON.search(item["question"]):
            singles.setdefault(item["source"], []).append(item)
    for item in stratified(rng, singles, SINGLE_SAMPLE):
        add(item["eval_id"], "단일 FAQ", item["source"], [item["question"]])

    clarification = load("scripts/clarification-evaluation/scenarios.json")
    for scenario in clarification:
        if any(step["expect"] == "ANSWER" for step in scenario["steps"]):
            add("CLAR-" + scenario["id"], "되묻기", "clarification-evaluation",
                [step["say"] for step in scenario["steps"]])

    flows = json.load(gzip.open(ROOT / "scripts/routing-call-comparison/results/step5-chat-flows/results.json.gz",
                                "rt", encoding="utf-8"))["gptoss-1"]["records"]
    for record in flows:
        if record["group"] != "store":
            add("FLOW-" + record["id"], "대화 흐름", "chat-flows-22", [turn["user"] for turn in record["turns"]])

    cases = load("scripts/context-resolution-evaluation/cases.json")
    groups = {}
    for key, value in cases.items():
        if isinstance(value, list) and value and isinstance(value[0], dict) and "expectedRelation" in value[0]:
            for case in value:
                if case["expectedRelation"] != "CLARIFICATION_REQUIRED" and case.get("history"):
                    groups.setdefault(key, []).append(case)
    for case in stratified(rng, groups, CONTEXT_SAMPLE):
        add("CTX-" + case["id"], "멀티턴", "context-resolution-evaluation", list(case["history"]) + [case["question"]])

    out = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "scripts/answer-generation-evaluation/scenarios-b.json"
    out.write_text(json.dumps(scenarios, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"시나리오 {len(scenarios)}개, 고객 발언 {sum(len(s['turns']) for s in scenarios)}개 → {out}")
    print("구성:", dict(Counter(s["group"] for s in scenarios)))


if __name__ == "__main__":
    main()
