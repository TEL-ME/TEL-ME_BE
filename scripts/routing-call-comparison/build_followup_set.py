"""TELME-175 4단계 후속 분석 평가셋(scripts/data/eval_routing_followup.json)을 만든다.

후속 분석 호출(FOLLOW_UP_SYSTEM_PROMPT)은 되묻는 중인 조건 이름과 고객 답변만 받아, 답변 종류
(CONDITION_RESPONSE 조건 답변 / DEFERRED 보류 / NEW_QUESTION 새 질문)와 조건별 상태(FILLED·DECLINED)를 낸다.
실제 입력(source=flow-db)은 TELME-174 대화 흐름 22개 실행 DB(consult_conditions)에 남은 조건 이름과 고객 답변이다.
여기에 지역·업무·예아니요 조건, 거절, 보류, 새 질문 답변을 더 썼다(source=written). 정답은 모델 결과를 보기 전에 정했다.

정답 형식
  response_types: 맞는 답변 종류(둘 이상이면 어느 것이든 맞음)
  conditions: {조건 이름: {"status": FILLED | DECLINED | ABSENT, "value": 기대 값(지역·업무만), "match": exact | contains}}
  ABSENT는 그 조건을 채우면 틀린 것이다(예: 업무 이름을 지역 값으로 넣음).

사용: python scripts/routing-call-comparison/build_followup_set.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "scripts/data/eval_routing_followup.json"

CR, DF, NQ = "CONDITION_RESPONSE", "DEFERRED", "NEW_QUESTION"


def filled(value=None, match="exact"):
    return {"status": "FILLED", **({"value": value, "match": match} if value else {})}


DECLINED = {"status": "DECLINED"}
ABSENT = {"status": "ABSENT"}

# (출처, 되묻는 조건, 고객 답변, 맞는 답변 종류, 조건 정답, 메모)
CASES = [
    # TELME-174 대화 흐름 DB의 실제 조건 이름과 답변
    ("flow-db", ["location"], "강남역", [CR], {"location": filled("강남역")}, ""),
    ("flow-db", ["agent_presents"], "아니요", [CR], {"agent_presents": filled()}, ""),
    ("flow-db", ["visit_together"], "예", [CR], {"visit_together": filled()}, ""),
    ("flow-db", ["location"], "명의변경이라고", [CR], {"location": ABSENT, "serviceType": filled("NAME_CHANGE")},
     "지역을 물었는데 업무로 답함. 실제 실행에서 '명의변경이라고'가 지역 값으로 들어갔다"),
    ("flow-db", ["six_months_passed"], "개통한 지 1년 넘었고 남은 할부금은 없어요", [CR], {"six_months_passed": filled()}, ""),
    ("flow-db", ["unpaid_bill"], "미납 요금은 없고 개통한 지 2년 됐어요", [CR], {"unpaid_bill": filled()}, ""),
    ("flow-db", ["over_12_months"], "24개월 약정 중에 10개월 썼어요", [CR], {"over_12_months": filled()}, ""),
    ("flow-db", ["self_pickup"], "유심을 잃어버렸고 제가 직접 매장에 갈 거예요", [CR], {"self_pickup": filled()}, ""),
    ("flow-db", ["age_at_least_14"], "만 15세이고 부모님이랑 같이 갈 거예요", [CR], {"age_at_least_14": filled()}, ""),
    ("flow-db", ["both_present"], "양도인이랑 양수인 둘 다 직접 매장에 갈 거예요", [CR], {"both_present": filled()}, ""),
    ("flow-db", ["both_present"], "직접 매장에 가요", [CR], {"both_present": filled()},
     "실제 실행 한 번은 DECLINED로 처리됨. 직접 간다는 답이라 거절이 아니다"),
    ("flow-db", ["past_due"], "납부일이 지난 지 20일 됐어요", [CR], {"past_due": filled()}, ""),
    ("flow-db", ["contract_less_12"], "24개월 약정 중에 10개월 썼어요", [CR], {"contract_less_12": filled()}, ""),
    ("flow-db", ["both_parties_present"], "둘 다 직접 가요", [CR], {"both_parties_present": filled()}, ""),
    # 지역
    ("written", ["location"], "서초동 쪽이요", [CR], {"location": filled("서초동")}, ""),
    ("written", ["location"], "부산 해운대 근처요", [CR], {"location": filled("해운대", "contains")}, ""),
    ("written", ["location"], "그건 알려드리기 곤란해요", [CR], {"location": DECLINED}, ""),
    ("written", ["location"], "잠깐만요 주소 좀 볼게요", [DF], {"location": ABSENT}, ""),
    ("written", ["location"], "지금 있는 곳 기준으로 찾아줘", [CR, DF], {"location": ABSENT},
     "기준점만 가리키는 말은 지역 값이 아니다"),
    ("written", ["location"], "요금제 변경은 어떻게 해요?", [NQ], {"location": ABSENT}, ""),
    ("written", ["location"], "신촌에서 번호이동 하려고요", [CR], {"location": filled("신촌"), "serviceType": filled("PORT_IN")}, ""),
    ("written", ["location"], "합정역 근처 매장에서 유심 새로 받으려고요", [CR],
     {"location": filled("합정역"), "serviceType": filled("USIM_REISSUE")}, ""),
    ("written", ["location"], "유심 재발급이요", [CR], {"location": ABSENT, "serviceType": filled("USIM_REISSUE")},
     "지역을 물었는데 업무로 답함"),
    # 업무
    ("written", ["serviceType"], "유심이 망가져서 새로 받으려고요", [CR], {"serviceType": filled("USIM_REISSUE")}, ""),
    ("written", ["serviceType"], "다른 통신사에서 옮겨 오려고요", [CR], {"serviceType": filled("PORT_IN")}, ""),
    ("written", ["serviceType"], "폰을 처음 새로 만들려고요", [CR], {"serviceType": filled("NEW_LINE")}, ""),
    ("written", ["serviceType"], "엄마 폰을 제 명의로 돌리려고요", [CR], {"serviceType": filled("NAME_CHANGE")}, ""),
    ("written", ["serviceType"], "로밍 요금은 얼마예요?", [NQ], {"serviceType": ABSENT}, ""),
    # 예·아니요와 값 조건
    ("written", ["unpaid_bill"], "없어요 다 냈어요", [CR], {"unpaid_bill": filled()}, ""),
    ("written", ["unpaid_bill"], "조금 밀린 게 있긴 해요", [CR], {"unpaid_bill": filled()}, ""),
    ("written", ["unpaid_bill"], "번호이동 수수료는 얼마예요?", [NQ], {"unpaid_bill": ABSENT}, ""),
    ("written", ["unpaid_bill"], "그건 왜 물어보시는 거예요?", [DF], {"unpaid_bill": ABSENT}, "답하지 않고 이유를 물음"),
    ("written", ["six_months_passed"], "3개월밖에 안 됐어요", [CR], {"six_months_passed": filled()}, ""),
    ("written", ["six_months_passed"], "고마워요", [DF], {"six_months_passed": ABSENT}, ""),
    ("written", ["self_pickup"], "동생이 대신 갈 거예요", [CR], {"self_pickup": filled()}, ""),
    ("written", ["self_pickup"], "잠시만요 확인해 볼게요", [DF], {"self_pickup": ABSENT}, ""),
    ("written", ["age_at_least_14"], "중학교 1학년이에요", [CR], {"age_at_least_14": filled()}, ""),
    ("written", ["age_at_least_14"], "나이는 말하고 싶지 않아요", [CR], {"age_at_least_14": DECLINED}, ""),
    ("written", ["both_present"], "저만 가고 아버지는 못 가세요", [CR], {"both_present": filled()}, ""),
    ("written", ["both_present"], "아직 정하지 못했어요", [DF, CR], {"both_present": ABSENT}, "정하지 못했다는 보류"),
    ("written", ["agent_visiting"], "아니요 둘 다 직접 가요", [CR], {"agent_visiting": filled()}, ""),
    ("written", ["agent_visiting"], "네 아내가 대신 가요", [CR], {"agent_visiting": filled()}, ""),
    ("written", ["joined_this_month"], "아니요 작년에 가입했어요", [CR], {"joined_this_month": filled()}, ""),
    ("written", ["changed_this_month"], "이번 달엔 안 바꿨어요", [CR], {"changed_this_month": filled()}, ""),
    ("written", ["travel_days"], "일본에 5일 가요", [CR], {"travel_days": filled()}, ""),
    ("written", ["travel_days"], "그런데 해외에서 데이터 차단은 어떻게 해요?", [NQ], {"travel_days": ABSENT}, ""),
    ("written", ["installment_settled"], "할부는 지난달에 다 갚았어요", [CR], {"installment_settled": filled()}, ""),
    ("written", ["over_12_months"], "아직 1년 안 됐어요", [CR], {"over_12_months": filled()}, ""),
]

# 후속 분석 프롬프트를 고치기 전에 쓴 확인용 답변(프롬프트 수정에 쓰지 않음).
# 설명이 붙은 부정 답, 진짜 거절·보류, 유심 새로 받기와 신규 개통 구분
CASES += [
    ("prompt-check", ["agent_visiting"], "아뇨 제가 직접 갈 거예요", [CR], {"agent_visiting": filled()}, ""),
    ("prompt-check", ["unpaid_bill"], "없어요 지난달에 다 냈어요", [CR], {"unpaid_bill": filled()}, ""),
    ("prompt-check", ["six_months_passed"], "아니요 이제 두 달 됐어요", [CR], {"six_months_passed": filled()}, ""),
    ("prompt-check", ["over_12_months"], "아뇨 아직 반년밖에 안 됐어요", [CR], {"over_12_months": filled()}, ""),
    ("prompt-check", ["joined_this_month"], "아니요 이번 달 아니에요", [CR], {"joined_this_month": filled()}, ""),
    ("prompt-check", ["self_pickup"], "아니요 남편이 받으러 갈 거예요", [CR], {"self_pickup": filled()}, ""),
    ("prompt-check", ["age_at_least_14"], "아니요 열두 살이에요", [CR], {"age_at_least_14": filled()}, ""),
    ("prompt-check", ["both_present"], "아니요 한 명만 갈 수 있어요", [CR], {"both_present": filled()}, ""),
    ("prompt-check", ["installment_settled"], "아뇨 아직 할부 남았어요", [CR], {"installment_settled": filled()}, ""),
    ("prompt-check", ["unpaid_bill"], "그런 건 대답하기 싫어요", [CR], {"unpaid_bill": DECLINED}, ""),
    ("prompt-check", ["age_at_least_14"], "개인정보라 말씀 못 드려요", [CR], {"age_at_least_14": DECLINED}, ""),
    ("prompt-check", ["agent_visiting"], "그건 나중에 말씀드릴게요", [DF], {"agent_visiting": ABSENT}, ""),
    ("prompt-check", ["serviceType"], "유심을 새 걸로 바꾸고 싶어요", [CR], {"serviceType": filled("USIM_REISSUE")}, ""),
    ("prompt-check", ["serviceType"], "eSIM으로 새로 발급받으려고요", [CR], {"serviceType": filled("USIM_REISSUE")}, ""),
    ("prompt-check", ["serviceType"], "번호 하나 새로 개통하려고요", [CR], {"serviceType": filled("NEW_LINE")}, ""),
    ("prompt-check", ["serviceType"], "아이 폰을 새로 하나 만들어 주려고요", [CR], {"serviceType": filled("NEW_LINE")}, ""),
    ("prompt-check", ["location"], "여의도역 근처에서 유심 새로 받을래요", [CR],
     {"location": filled("여의도역"), "serviceType": filled("USIM_REISSUE")}, ""),
    ("prompt-check", ["location"], "목동에서 새 회선 하나 만들려고요", [CR],
     {"location": filled("목동"), "serviceType": filled("NEW_LINE")}, ""),
    ("prompt-check", ["location"], "망원동에서 유심만 바꾸면 돼요", [CR],
     {"location": filled("망원동"), "serviceType": filled("USIM_REISSUE")}, ""),
    ("prompt-check", ["past_due"], "아니요 아직 납부일 안 지났어요", [CR], {"past_due": filled()}, ""),
]

items = []
for number, (source, keys, reply, types, conditions, note) in enumerate(CASES, 1):
    item = {"case_id": f"FU-{number:02d}", "expected_intent": "FAQ", "source": source, "pending_keys": keys,
            "reply": reply, "response_types": types, "conditions": conditions}
    if note:
        item["note"] = note
    items.append(item)
result = {
    "description": "TELME-175 4단계 후속 분석 평가셋. 실제 입력은 TELME-174 대화 흐름 DB의 조건 이름과 답변, 나머지는 새로 썼다. "
                   "정답은 사람 검토 전이다.",
    "counts": {"cases": len(items), "flow_db": sum(i["source"] == "flow-db" for i in items),
               "by_response_type": {t: sum(i["response_types"][0] == t for i in items) for t in (CR, DF, NQ)}},
    "cases": items,
}
OUT.write_text(json.dumps(result, ensure_ascii=False, indent=1), encoding="utf-8")
print(result["counts"])
