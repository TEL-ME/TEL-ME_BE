#!/usr/bin/env python3
"""매장 찾기·되묻기·로그인 회원 흐름 평가셋 v1을 만든다. 모델 답변이나 Judge 판정은 넣지 않는다.

원본은 flow_v1_sources/flows_written.json이고, Judge 프롬프트·평가 기준을 보지 않은 별도 작성자(에이전트)가 썼다.
- store_flow: 매장 찾기. 앱이 업무(serviceType)와 지역(location)을 되물으면 고객 답변을 보낸다. 지역은 '현재 위치 사용'
  (좌표)이나 지명으로 답한다. 지명 검색은 카카오 로컬 API가 필요해 requiresKakao로 표시한다.
- clarification_flow: FAQ 답이 고객 조건에 따라 갈리는 질문과 요금제 추천. 앱이 조건을 되물으면 고객 답변을 보낸다.
- member_data: 로그인한 테스트 회원(actor=MEMBER)의 내 요금제·사용량·약정·위약금 질문과 회원 데이터가 없는 질문.
되묻기 답변(clarificationReplies)은 앱이 되물을 때만 쓰고, 마지막 응답을 판정한다. 회원 답변의 인정 근거는 캡처할 때
남긴 회원 계정 데이터라 라벨에는 sourceId만 적고 판정할 때 채운다.
v2는 v1 36개에 별도 작성자가 쓴 52개(flows_v2_written.json)를 더한다. 매장 30, 되묻기 30, 회원 28개 대화다.
요금제 추천 시나리오의 고객 답변 두 개는 앱의 조건 이름(age_group, heavy_data)에 순서대로 연결한다.
v1 파일은 고정해 두고 build(version=1)로 다시 만들 수 있다.
사용: python -X utf8 -m scripts.chat_judge.build_service_flow_set
"""
import json
from pathlib import Path

from scripts.chat_judge import service_data

ROOT = Path(__file__).resolve().parents[2]
SOURCES = ROOT / "scripts/chat_judge/data/flow_v1_sources"
CATALOG = ROOT / "scripts/data/faq_full_1150.json"
PLANS = ROOT / "scripts/chat_judge/data/quality_v2_sources/plans_snapshot.json"
OUTPUT_V1 = ROOT / "scripts/chat_judge/data/chat_pipeline_flows_v1.json"
OUTPUT = ROOT / "scripts/chat_judge/data/chat_pipeline_flows_v2.json"
PROVENANCE = {"flows_written.json": "flow_v1_agent_written_scenario; human_review_pending",
              "flows_v2_written.json": "flow_v2_agent_written_scenario; human_review_pending"}
# 요금제 추천 되묻기의 조건 이름(MemberRecommendationAnswerer.AGE_KEY, HEAVY_KEY). 시나리오는 나이, 데이터 순으로 답한다
RECOMMENDATION_KEYS = ("age_group", "heavy_data")
# '현재 위치 사용' 프리셋. 평가 DB(telme_judge_eval)에서 그 지역 매장 좌표의 평균이다
CURRENT_LOCATIONS = {
    "강남구": (37.5081, 127.0359), "영등포구": (37.5050, 126.9015), "해운대구": (35.1919, 129.1434),
    "의정부시": (37.7453, 127.0506), "대전 중구": (36.3228, 127.4175), "양천구": (37.5199, 126.8563),
}
# 질문에 업무를 말했어도 앱이 업무를 되물으면 고객은 다시 답한다. 시나리오에 그 답변이 없으면 기대 업무로 채운다(auto)
SERVICE_TYPE_REPLIES = {"NEW_LINE": "신규가입이요", "PORT_IN": "번호이동이요", "NAME_CHANGE": "명의변경이요",
                        "USIM_REISSUE": "유심 재발급이요", None: "특별한 업무는 없어요"}
SUITE_CATEGORY = {"store_flow": "STORE_FLOW", "clarification_flow": "CLARIFICATION_FLOW", "member_data": "MEMBER_DATA"}


def reply(row):
    result = {"whenKey": row.get("whenKey"), "content": row["content"]}
    if row.get("currentLocation"):
        latitude, longitude = CURRENT_LOCATIONS[row["currentLocation"]]
        result.update(currentLocation=row["currentLocation"],
                      coordinates={"latitude": latitude, "longitude": longitude})
    return result


def references(case, turn, catalog, plan_alternative):
    question = turn["question"]
    behavior = turn["expectedBehavior"]
    if behavior in {"STORE_LOOKUP", "ABSTAIN"}:
        return [], []
    if case["suite"] == "member_data":
        return [], [{"questionPart": question, "alternatives": [{"sourceId": service_data.MEMBER_SOURCE_ID}]}]
    slots = turn.get("faqSlotIds") or []
    if not slots:  # 요금제 추천
        return [], [{"questionPart": question, "alternatives": [dict(plan_alternative)]}]
    unknown = [slot for slot in slots if slot not in catalog]
    if unknown:
        raise ValueError(f"{case['id']}: 카탈로그에 없는 FAQ {unknown}")
    return [slots], [{"questionPart": question,
                      "alternatives": [{"sourceId": slot, "answer": catalog[slot]["answer"]} for slot in slots]}]


def answer_sources(case, turn):
    if turn["expectedBehavior"] == "STORE_LOOKUP":
        return ["STORE"]
    if turn["expectedBehavior"] == "ABSTAIN":
        return []
    if case["suite"] == "member_data":
        return ["MEMBER"]
    return ["FAQ"] if turn.get("faqSlotIds") else ["PLAN_TABLE"]


def recommendation_keys(case, turn, replies):
    """요금제 추천 시나리오의 조건 이름 없는 답변 두 개에 앱 조건 이름을 붙인다. 앱이 묻는 순서가 바뀌어도 맞는 답을 고른다."""
    unkeyed = [row for row in replies if row["whenKey"] is None]
    if case["suite"] == "clarification_flow" and not turn.get("faqSlotIds") and len(unkeyed) == 2:
        for row, key in zip(unkeyed, RECOMMENDATION_KEYS):
            row["whenKey"] = key
    return replies


def build(version=2):
    catalog = {faq["slot_id"]: faq for faq in json.loads(CATALOG.read_text(encoding="utf-8"))}
    plans = json.loads(PLANS.read_text(encoding="utf-8"))
    plan_source = service_data.plan_table_source(plans["plans"], plans["planEligibility"])
    plan_alternative = {key: plan_source[key] for key in ("sourceId", "answer")}
    names = ["flows_written.json"] + (["flows_v2_written.json"] if version >= 2 else [])
    written = {"cases": [dict(case, source=name) for name in names
                         for case in json.loads((SOURCES / name).read_text(encoding="utf-8"))["cases"]]}
    # 앱이 되묻지 않고 바로 답했을 때의 필수 사실(갈리는 경우를 모두 안내). 별도 작성자가 FAQ 본문으로 썼다
    direct_path = SOURCES / "direct_answer_facts.json"
    direct = ({row["id"]: row["factsIfAnsweredDirectly"]
               for row in json.loads(direct_path.read_text(encoding="utf-8"))["clarificationCases"]}
              if direct_path.exists() else {})
    # 사람 검토로 바꾼 필수 사실(review_decisions.json). 원본 시나리오는 그대로 둔다
    decisions_path = SOURCES / "review_decisions.json"
    overrides = (json.loads(decisions_path.read_text(encoding="utf-8"))["requiredFactsOverrides"]
                 if decisions_path.exists() else {})
    # v2 시나리오를 다른 검수자가 FAQ와 대조한 결과(flows_v2_verified.json). 고친 필수 사실을 쓰고 메모를 남긴다
    verified_path = SOURCES / "flows_v2_verified.json"
    verified = {}
    if version >= 2 and verified_path.exists():
        data = json.loads(verified_path.read_text(encoding="utf-8"))
        verified = {row["id"]: row for kind in ("flowFaq", "memberNoData") for row in data[kind]}
    cases = []
    for case in written["cases"]:
        turns = []
        for turn in case["turns"]:
            gold, quality = references(case, turn, catalog, plan_alternative)
            replies = [reply(row) for row in turn.get("clarificationReplies", [])]
            if version >= 2:
                replies = recommendation_keys(case, turn, replies)
            # 바로 답했을 때 필수 사실: v1은 별도 파일, v2 시나리오는 작성자가 함께 썼다
            check = verified.get(case["id"]) if turn is case["turns"][0] else None
            direct_facts = direct.get(case["id"]) or turn.get("factsIfAnsweredDirectly")
            facts = overrides[case["id"]]["requiredFacts"] if case["id"] in overrides else turn.get("requiredFacts", [])
            if check and check["verdict"] == "FIX":
                facts, direct_facts = check["requiredFacts"], check.get("factsIfAnsweredDirectly") or direct_facts
            note = " / ".join(filter(None, [turn.get("note"), check and check["verdict"] != "OK"
                                            and f"검수({check['verdict']}): {check['reason']}"]))
            if case["suite"] == "store_flow" and not any(row["whenKey"] == "serviceType" for row in replies):
                replies.append({"whenKey": "serviceType", "content": SERVICE_TYPE_REPLIES[turn.get("expectedServiceType")],
                                "auto": True})
            turns.append({
                "question": turn["question"],
                "expectedIntent": "STORE" if turn["expectedBehavior"] == "STORE_LOOKUP" else "FAQ",
                "expectedBehavior": turn["expectedBehavior"],
                "goldSourceGroups": gold,
                "requiredFacts": facts,
                **({"requiredFactsIfAnsweredDirectly": direct_facts} if direct_facts else {}),
                "qualityReferenceGroups": quality,
                "clarificationReplies": replies,
                "expectedServiceType": turn.get("expectedServiceType"),
                "answerSources": answer_sources(case, turn),
                "labelProvenance": PROVENANCE[case["source"]],
                **({"note": note} if note else {})})
        cases.append({"caseId": f"FLOW-{case['id']}", "category": SUITE_CATEGORY[case["suite"]],
                      "questionType": case["suite"].upper(), "suite": case["suite"],
                      "actor": case.get("actor", "GUEST"), "requiresKakao": bool(case.get("requiresKakao")),
                      "scenario": case.get("scenario"), "turns": turns})
    if len({case["caseId"] for case in cases}) != len(cases):
        raise ValueError("Duplicate case ID")
    return cases


def main():
    OUTPUT_V1.write_text(json.dumps(build(version=1), ensure_ascii=False, indent=1) + "\n", encoding="utf-8",
                         newline="\n")
    cases = build()
    OUTPUT.write_text(json.dumps(cases, ensure_ascii=False, indent=1) + "\n", encoding="utf-8", newline="\n")
    suites = {}
    for case in cases:
        suites[case["suite"]] = suites.get(case["suite"], 0) + len(case["turns"])
    print(json.dumps({"cases": len(cases), "turnsBySuite": suites,
                      "requiresKakao": sum(case["requiresKakao"] for case in cases)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
