#!/usr/bin/env python3
"""서비스 답변 품질 평가셋 v2를 만든다. 모델 답변이나 Judge 판정은 넣지 않는다.

원본은 quality_v2_sources/에 있다. 질문과 필수 사실은 Judge 프롬프트·평가 기준을 보지 않은 별도 작성자(에이전트)가 썼다.
- 답변 가능 질문 120개: 분야마다 FAQ 12개를 골라 고객 말투로 다시 쓴 질문과 필수 사실(answerable_*.json)
- 복합 질문 20개: 위 질문 중 서로 다른 분야 두 개를 한 문장으로 묻는 질문
- 멀티턴 20개: 같은 분야 FAQ 두 개로 만든 2턴 대화, 15개는 둘째 턴이 대상을 생략(multiturn_*.json)
- FAQ로 답할 수 없는 질문: 별도 작성자가 쓰고, 다른 검수자가 가까운 FAQ 답변과 대조해 답이 없다고 본 것만 쓴다(unanswerable_*.json)
- 흐름 회귀 9개 대화: v1의 flow_regression을 그대로 둔다
답변 가능 질문의 정답 묶음은 별도 검수자가 정했다. 후보는 v1의 대체 FAQ 묶음, 첫 실행의 실제 검색 결과, 임베딩으로
가까운 FAQ 6개이고(equivalence_candidates.json), 답변 본문에 필수 사실이 모두 있는 FAQ만 인정했다(equivalence_labels.json).
질문을 쓸 때 쓴 FAQ는 모두 인정됐고, v1 대체 FAQ 중 필수 사실이 빠진 24개는 빠졌다.
v2.1은 v2에 서비스 데이터 라벨(service_data_labels.json)을 더한다. 턴마다 답의 출처(answerSources: FAQ, PLAN_TABLE,
MEMBER, STORE)를 적고, 요금제 표로 답이 정해지는 질문 부분에는 요금제 표(plans_snapshot.json)를 인정 근거로 더한다.
FAQ에 답이 없지만 요금제 표로 답할 수 있는 질문은 답변 질문으로 바꾼다. v2 파일은 고정해 두고 build(service_labels=False)로 다시 만들 수 있다.
사용: python -X utf8 -m scripts.chat_judge.build_service_quality_set
"""
import json
from pathlib import Path

from scripts.chat_judge import service_data

ROOT = Path(__file__).resolve().parents[2]
SOURCES = ROOT / "scripts/chat_judge/data/quality_v2_sources"
V1 = ROOT / "scripts/chat_judge/data/chat_pipeline_quality_v1.json"
OUTPUT_V2 = ROOT / "scripts/chat_judge/data/chat_pipeline_quality_v2.json"
OUTPUT = ROOT / "scripts/chat_judge/data/chat_pipeline_quality_v2_1.json"
CATALOG = ROOT / "scripts/data/faq_full_1150.json"
PROVENANCE = "v2_agent_written_question_and_required_facts; human_review_pending"


def load(name):
    return json.loads((SOURCES / name).read_text(encoding="utf-8"))


def references(part, group, catalog):
    """질문 부분 하나와 그 부분에 인정하는 대체 FAQ 답변들. FAQ 질문 문구는 넣지 않는다."""
    return {"questionPart": part,
            "alternatives": [{"sourceId": slot, "answer": catalog[slot]["answer"]} for slot in group]}


def answer_turn(question, behavior, groups_with_parts, facts, catalog):
    return {"question": question, "expectedIntent": "FAQ", "expectedBehavior": behavior,
            "goldSourceGroups": [group for _, group in groups_with_parts], "requiredFacts": facts,
            "qualityReferenceGroups": [references(part, group, catalog) for part, group in groups_with_parts],
            "labelProvenance": PROVENANCE}


def accepted_alternatives():
    """검수자가 인정한 대체 FAQ. 키는 '사례ID:턴:질문부분'이다. 파일이 없으면 v1 묶음만 쓴다."""
    path = SOURCES / "equivalence_labels.json"
    if not path.exists():
        return {}
    return {row["unit"]: row["accepted"] for row in json.loads(path.read_text(encoding="utf-8"))}


def widen(case, accepted):
    """검수자가 인정한 FAQ로 정답 묶음을 바꾼다. 원래 묶음에서 인정된 FAQ를 앞에 둔다. 검수 결과가 없으면 그대로 둔다."""
    for turn_index, turn in enumerate(case["turns"]):
        for part_index, group in enumerate(turn["goldSourceGroups"]):
            labels = accepted.get(f"{case['caseId']}:{turn_index}:{part_index}")
            if labels:
                group[:] = [slot for slot in group if slot in labels] + [slot for slot in labels if slot not in group]
    return case


def answer_sources(turn):
    if turn["expectedBehavior"] == "STORE_LOOKUP":
        return ["STORE"]
    return ["FAQ"] if turn.get("qualityReferenceGroups") else []


def apply_service_labels(cases):
    """요금제 표와 회원 정보로 답할 수 있는 질문의 라벨을 더한다(v2.1)."""
    labels = load("service_data_labels.json")
    snapshot = load("plans_snapshot.json")
    plan_source = service_data.plan_table_source(snapshot["plans"], snapshot["planEligibility"])
    plan_alternative = {key: plan_source[key] for key in ("sourceId", "answer")}
    by_id = {case["caseId"]: case for case in cases}
    for case in cases:
        for turn in case["turns"]:
            turn["answerSources"] = answer_sources(turn)
    marked = "; v2.1_service_data_labels"
    for row in labels["planTableParts"]:
        case_id, turn_index, part_index = row["unit"].rsplit(":", 2)
        turn = by_id[case_id]["turns"][int(turn_index)]
        turn["qualityReferenceGroups"][int(part_index)]["alternatives"].append(dict(plan_alternative))
        turn["answerSources"] = ["FAQ", "PLAN_TABLE"]
        turn["labelProvenance"] += marked
    for row in labels["planTableAnswers"]:
        turn = by_id[f"V2-NOANSWER-{row['id']}"]["turns"][0]
        turn.update(expectedBehavior="ANSWER", requiredFacts=row["requiredFacts"], answerSources=["PLAN_TABLE"],
                    qualityReferenceGroups=[{"questionPart": turn["question"], "alternatives": [dict(plan_alternative)]}])
        turn["labelProvenance"] += marked
    for row in labels["memberData"]:
        # 비로그인 실행은 로그인 안내가 정답(ABSTAIN). 로그인 실행에서는 회원 데이터로 답해야 한다
        turn = by_id[f"V2-NOANSWER-{row['id']}"]["turns"][0]
        turn.update(answerSources=["MEMBER"], memberExpectedBehavior="ANSWER")
        turn["labelProvenance"] += marked
    return cases


def build(service_labels=True):
    catalog = {faq["slot_id"]: faq for faq in json.loads(CATALOG.read_text(encoding="utf-8"))}
    seeds = {seed["key"]: seed for seed in load("answerable_seed.json")}
    written = load("answerable_written.json")
    cases = []
    for item in written["single"]:
        seed = seeds[item["key"]]
        cases.append({"caseId": f"V2-SINGLE-{item['key']}", "category": seed["category"], "questionType": "FACT",
                      "suite": "paraphrased_single", "turns": [answer_turn(
                          item["userQuestion"], item["expectedBehavior"],
                          [(item["userQuestion"], seed["goldGroup"])], item["requiredFacts"], catalog)]})
    singles = {item["key"]: item for item in written["single"]}
    for item in written["compound"]:
        parts = [(singles[part["key"]]["userQuestion"], seeds[part["key"]]["goldGroup"]) for part in item["parts"]]
        facts = [fact for part in item["parts"] for fact in part["requiredFacts"]]
        cases.append({"caseId": f"V2-COMPOUND-{item['id']}", "category": "COMPOUND", "questionType": "COMPOUND",
                      "suite": "paraphrased_compound",
                      "turns": [answer_turn(item["userQuestion"], "ANSWER", parts, facts, catalog)]})
    multi_seeds = {seed["key"]: seed for seed in load("multiturn_seed.json")}
    for item in load("multiturn_written.json"):
        seed = multi_seeds[item["key"]]
        turns = []
        for name in ("turn1", "turn2"):
            turn = item[name]
            gold = seed["first" if name == "turn1" else "second"]["goldGroup"]
            turns.append(answer_turn(turn["userQuestion"], turn["expectedBehavior"],
                                     [(turn["userQuestion"], gold)], turn["requiredFacts"], catalog))
        dependent = item["turn2"].get("dependsOnPrevious", False)
        cases.append({"caseId": f"V2-MULTI-{item['key']}", "category": seed["category"],
                      "questionType": "FOLLOW_UP_OMITTED" if dependent else "FOLLOW_UP_EXPLICIT",
                      "suite": "multiturn", "turns": turns})
    verified_path = SOURCES / "unanswerable_verified.json"
    excluded = []
    if verified_path.exists():
        verdicts = {row["id"]: row for row in json.loads(verified_path.read_text(encoding="utf-8"))}
        for item in load("unanswerable_written.json"):
            verdict = verdicts[item["id"]]["verdict"]
            if verdict != "NOT_ANSWERABLE":
                excluded.append({"id": item["id"], "verdict": verdict})
                continue
            outside = item["kind"] == "ADJACENT_OUT_OF_SCOPE"
            cases.append({"caseId": f"V2-NOANSWER-{item['id']}", "category": "OUT_OF_SCOPE" if outside else "NO_FAQ_ANSWER",
                          "questionType": item["kind"], "suite": "adjacent_out_of_scope" if outside else "no_faq_answer",
                          "turns": [{"question": item["userQuestion"], "expectedIntent": "UNKNOWN" if outside else "FAQ",
                                     "expectedBehavior": "OUT_OF_SCOPE" if outside else "ABSTAIN",
                                     "goldSourceGroups": [], "requiredFacts": [], "qualityReferenceGroups": [],
                                     "labelProvenance": PROVENANCE}]})
    accepted = accepted_alternatives()
    for case in cases:
        widen(case, accepted)
        for turn in case["turns"]:
            for group, reference in zip(turn["goldSourceGroups"], turn["qualityReferenceGroups"]):
                reference["alternatives"] = [{"sourceId": slot, "answer": catalog[slot]["answer"]} for slot in group]
    for case in json.loads(V1.read_text(encoding="utf-8")):
        if case.get("suite") == "flow_regression":
            cases.append(case)
    if len({case["caseId"] for case in cases}) != len(cases):
        raise ValueError("Duplicate case ID")
    if service_labels:
        apply_service_labels(cases)
    return cases, excluded


def main():
    cases, excluded = build()
    OUTPUT.write_text(json.dumps(cases, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
    suites = {}
    for case in cases:
        suites[case["suite"]] = suites.get(case["suite"], 0) + len(case["turns"])
    print(json.dumps({"cases": len(cases), "turnsBySuite": suites, "excludedUnanswerable": excluded}, ensure_ascii=False))


if __name__ == "__main__":
    main()
