"""TELME-175 3단계 검색어 검증 평가셋(scripts/data/eval_routing_faithfulness.json)을 만든다.

입력은 프로브의 faithfulness-cases 모드가 1단계 gpt-oss 1회차 의도 분류 출력에서 만든 검색어 검증 입력이다
(복합 FAQ 하위 질문 묶음, 원문 검색 0건일 때의 재검색 후보). 여기에 정답(gold_unsafe)을 붙이고,
gpt-oss가 만든 검색어에는 뜻이 바뀐 경우가 적어 뜻을 일부러 바꾼 검색어를 더한다.
정답은 모델 결과를 보기 전에 정했다. 기준은 FAQ_QUERY_FAITHFULNESS_PROMPT와 같다:
원문에 없는 조건·대상·순서를 넣거나, 조건·부정·비교 대상을 빠뜨리거나, 상황을 특정 해결책으로 좁히면 unsafe.
gold_unsafe=null은 판단이 갈리는 검색어로 어느 판정이든 맞은 것으로 본다.

사용: python scripts/routing-call-comparison/build_faithfulness_set.py [프로브가 만든 입력 파일]
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build/tmp/routing-call-comparison/faith-cases.json"
OUT = ROOT / "scripts/data/eval_routing_faithfulness.json"

# 실제 검색어 중 뜻이 바뀐 것: (eval_id, 하위 질문 순번 0부터) -> 이유
UNSAFE = {
    ("RU-023", 0): "조건 '가입한 지 얼마 안 됐는데'를 빠뜨림",
    ("RU-024", 0): "조건 '상담원 통해'를 빠뜨림",
    ("RU-027", 0): "조건 '회사 심부름(법인 업무 대리)'을 빠뜨림",
    ("RU-057", 0): "'인지세도 안 내나요'를 '인지세 안내 여부'로 바꿈",
    ("RU-059", 0): "원문에 없는 조건 '7700원 이하'를 넣음",
    ("RU-061", 0): "원문에 없는 업무 '가입'을 넣음",
    ("RU-064", 0): "조건 '약정 반 넘게 채웠으면'을 '약정 초과 시'로 바꿈",
    ("RU-176", 0): "부정 '가입은 안 하고 해지만'을 '가입 방법'으로 뒤집음",
    ("RU-209", 0): "비교 대상(빨리 해지 vs 더 쓰다 해지)을 빠뜨림",
    ("RU-214", 0): "조건 '폰 바꿀 때'와 비교 대상(그대로 쓰기 vs 새로 받기)을 일반 비교로 바꿈",
    ("RU-215", 0): "조건 '폰 잃어버렸는데'를 빠뜨림",
    ("RU-221", 0): "원문에 없는 비교 기준 '비용'을 넣음",
    ("RU-311", 0): "원문에 없는 지역 '강남역'을 넣음",
    ("RU-331", 1): "조건 '데이터 다 쓰면'을 '데이터 사용 시'로 바꿈",
}
# 판단이 갈리는 검색어 -> 이유
EITHER = {
    ("RU-010", 0): "'발급받을 수 있나요'를 '발급 방법'으로 바꿈(가능 여부를 전제로 바꿨는지 애매)",
    ("RU-204", 0): "'제 명의로'를 '제명으로'로 붙여 씀(뜻은 같지만 다른 단어로 읽힐 수 있음)",
    ("RU-355", 0): "'어떻게 되나요'를 '적용 방법'으로 바꿈",
    ("RU-322", 0): "질문 전체 조건 '기기변경할 때'가 이 하위 질문에서 빠짐",
    ("RU-337", 1): "공통 대상 '인터넷'이 빠져 '요금제 속도'만 남음",
    ("RU-349", 1): "공통 대상 '데이터 쉐어링'이 빠짐",
    ("RU-350", 1): "공통 대상 '청소년 요금제'가 빠짐",
}
# 뜻을 일부러 바꾼 검색어. refined는 (원문, 검색어, 이유), compound는 (원문, [(인용, 검색어, unsafe, 이유)])
SYNTHETIC_REFINED = [
    ("미납 요금이 있는데 기기변경 할 수 있나요?", "기기변경 가능 여부", "조건 빠뜨림"),
    ("해외에 있는데 유심 재발급 받을 수 있나요?", "유심 재발급 방법", "조건 빠뜨림"),
    ("법인 폰인데 명의변경 서류가 뭐예요?", "명의변경 필요 서류", "조건 빠뜨림"),
    ("약정 1년 남았는데 요금제 낮추면 위약금 있나요?", "요금제 변경 위약금", "조건 빠뜨림"),
    ("번호는 바꾸지 않고 통신사만 옮기고 싶어요", "번호 변경 방법", "부정 빠뜨림"),
    ("인터넷은 유지하고 TV만 해지하려고요", "인터넷 TV 해지 방법", "부정·대상 범위 바꿈"),
    ("부가서비스 해지 말고 잠깐 멈출 수 있어요?", "부가서비스 해지 방법", "부정 빠뜨림"),
    ("로밍 요금이 얼마예요?", "일본 로밍 요금", "없는 대상 넣음"),
    ("유심 재발급 비용 알려줘", "eSIM 재발급 비용", "대상 바꿈"),
    ("데이터 쉐어링 신청 방법 알려주세요", "태블릿 데이터 쉐어링 신청 방법", "없는 기기 넣음"),
    ("요금제 변경은 언제 적용돼요?", "5G 요금제 변경 다음 달 적용 여부", "없는 상품·시점 넣음"),
    ("명의변경 수수료 얼마예요?", "가족 간 명의변경 수수료 면제 조건", "없는 조건 넣음"),
    ("폰을 잃어버렸어요", "분실폰 할부금 납부 방법", "상황을 특정 해결책으로 좁힘"),
    ("요금이 너무 많이 나왔어요", "요금제 변경 방법", "상황을 특정 해결책으로 좁힘"),
    ("인터넷이 계속 끊겨요", "인터넷 해지 방법", "상황을 특정 해결책으로 좁힘"),
    ("해외에서 데이터가 안 돼요", "로밍 패스 가입 방법", "상황을 특정 해결책으로 좁힘"),
    ("선불폰이랑 후불폰 중 뭐가 싸요?", "선불폰 요금", "비교를 한쪽 설명으로 바꿈"),
    ("매장 개통이랑 온라인 개통 중 뭐가 빨라요?", "온라인 개통 방법", "비교를 한쪽 설명으로 바꿈"),
    ("분실 신고랑 일시정지 차이 알려줘", "분실 신고 방법", "비교를 한쪽 설명으로 바꿈"),
    ("가족결합 할인이랑 선택약정 할인 같이 받을 수 있어요?", "가족결합 할인 후 선택약정 적용 순서", "없는 순서 넣음"),
]
SYNTHETIC_COMPOUND = [
    ("로밍 신청 방법이랑 해지 위약금 알려줘", [
        ("로밍 신청 방법", "로밍 신청 방법", False, ""),
        ("해지 위약금", "로밍 해지 위약금", True, "공통인지 불분명한 앞 대상 '로밍'을 붙임")]),
    ("유심 재발급 비용이랑 분실 신고 방법 알려줘", [
        ("유심 재발급 비용", "유심 재발급 비용", False, ""),
        ("분실 신고 방법", "유심 분실 신고 방법", True, "공통인지 불분명한 앞 대상 '유심'을 붙임")]),
    ("eSIM 발급 비용하고 개통 취소 방법 알려줘", [
        ("eSIM 발급 비용", "eSIM 발급 비용", False, ""),
        ("개통 취소 방법", "eSIM 개통 취소 방법", True, "공통인지 불분명한 앞 대상 'eSIM'을 붙임")]),
    ("미납 요금 있는데 번호이동 방법이랑 필요 서류 알려줘", [
        ("번호이동 방법", "번호이동 방법", True, "질문 전체 조건 '미납 요금 있는데'를 빠뜨림"),
        ("필요 서류", "번호이동 필요 서류", True, "질문 전체 조건 '미납 요금 있는데'를 빠뜨림")]),
    ("로밍 신청 방법 알려주고 5G랑 LTE 데이터 차이도 알려줘", [
        ("로밍 신청 방법", "로밍 신청 방법", False, ""),
        ("5G랑 LTE 데이터 차이", "5G 데이터 제공량", True, "비교를 한쪽 설명으로 바꿈")]),
]

# 검색어 검증 프롬프트를 고치기 전에 쓴 확인용 검색어(프롬프트 수정에 쓰지 않음). gpt-oss가 실제로 만드는
# 문체처럼 미묘하게 바꾼 것과 멀쩡한 바꿔 말하기를 섞었다
CHECK_REFINED = [
    ("군대 가는데 휴대폰 정지하면 요금 나와요?", "휴대폰 정지 시 요금 발생 여부", True, "조건 '군대 가는데'를 빠뜨림"),
    ("해외 체류 중인데 명의변경 할 수 있나요?", "명의변경 가능 여부", True, "조건 '해외 체류 중인데'를 빠뜨림"),
    ("자녀가 만 14세 미만인데 혼자 개통할 수 있어요?", "자녀 혼자 개통 가능 여부", True, "조건 '만 14세 미만'을 빠뜨림"),
    ("약정 끝나기 한 달 전인데 해지하면 위약금 나와요?", "해지 시 위약금 발생 여부", True, "조건 '약정 끝나기 한 달 전'을 빠뜨림"),
    ("외국인인데 선불폰도 번호이동 되나요?", "선불폰 번호이동 가능 여부", True, "조건 '외국인'을 빠뜨림"),
    ("온라인으로 산 폰인데 매장에서 개통 취소돼요?", "개통 취소 가능 여부", True, "조건 '온라인 구매'와 '매장에서'를 빠뜨림"),
    ("데이터 선물하기 한도 얼마예요?", "데이터 선물하기 월 2GB 한도 여부", True, "원문에 없는 기준 '월 2GB'를 넣음"),
    ("로밍 요금 언제 청구돼요?", "로밍 요금 다음 달 청구 여부", True, "원문에 없는 시점 '다음 달'을 넣음"),
    ("명의변경할 때 대리인도 돼요?", "명의변경 대리인 위임장 양식", True, "가능 여부를 특정 서류 양식으로 좁힘"),
    ("요금제 바꾸면 결합 할인 유지돼요?", "요금제 변경 시 가족결합 할인 유지 여부", True, "결합 할인을 '가족결합'으로 좁힘"),
    ("유심 재발급 비용 얼마예요?", "유심 재발급 택배 비용", True, "원문에 없는 '택배'를 넣음"),
    ("번호이동하면 멤버십 등급 유지돼요?", "번호이동 시 VIP 등급 유지 여부", True, "등급을 'VIP'로 좁힘"),
    ("폰이 물에 빠졌는데 어떻게 해야 돼요?", "휴대폰 침수 시 처리 방법", False, ""),
    ("요금 자동이체 날짜 바꿀 수 있어요?", "요금 자동이체 날짜 변경 가능 여부", False, ""),
    ("데이터 다 쓰면 추가로 살 수 있어요?", "데이터 소진 후 추가 구매 가능 여부", False, ""),
    ("유심 바꾸면 전화번호 그대로예요?", "유심 교체 시 전화번호 유지 여부", False, ""),
    ("명의변경하는데 둘 다 매장 가야 돼요?", "명의변경 시 양측 매장 방문 필요 여부", False, ""),
    ("해지하면 남은 할부금은 어떻게 내요?", "해지 시 남은 할부금 납부 방법", False, ""),
    ("로밍 켜 두면 요금 자동으로 나가요?", "로밍 설정 시 요금 자동 부과 여부", False, ""),
    ("청소년 요금제랑 일반 요금제 중 뭐가 데이터 많이 줘요?", "청소년 요금제와 일반 요금제 데이터 제공량 비교", False, ""),
    ("약정 없이 가입하면 요금이 더 비싸요?", "무약정 가입 시 요금 차이", False, ""),
    ("미성년자 개통할 때 부모님이 같이 가야 해요?", "미성년자 개통 시 법정대리인 동행 필요 여부", False, ""),
    ("가족결합은 몇 명까지 돼요?", "가족결합 가능 인원", False, ""),
    ("청구서를 문자로 받을 수 있나요?", "청구서 문자 수신 가능 여부", False, ""),
]
CHECK_COMPOUND = [
    ("로밍 패스 가입 방법이랑 번호 변경 수수료 알려줘", [
        ("로밍 패스 가입 방법", "로밍 패스 가입 방법", False, ""),
        ("번호 변경 수수료", "로밍 번호 변경 수수료", True, "공통인지 불분명한 앞 대상 '로밍'을 붙임")]),
    ("외국인인데 개통 서류랑 요금제 종류 알려줘", [
        ("개통 서류", "개통 서류", True, "질문 전체 조건 '외국인'을 빠뜨림"),
        ("요금제 종류", "요금제 종류", None, "'외국인' 조건이 요금제 종류에도 걸리는지 애매")]),
    ("번호이동 비용이랑 소요 시간 알려줘", [
        ("번호이동 비용", "번호이동 비용", False, ""),
        ("소요 시간", "번호이동 소요 시간", False, "")]),
]

cases = json.loads(SOURCE.read_text(encoding="utf-8"))
# 원문이 검색어 검증 프롬프트 예시인 문항은 평가셋에서 3단계 집계 제외로 표시해 두었다
prompt_examples = {i["eval_id"] for i in json.loads((ROOT / "scripts/data/eval_routing_unified.json")
                                                     .read_text(encoding="utf-8"))["items"]
                   if any("근거 검증 프롬프트 예시" in t for t in i["tags"])}
used = set()
for case in cases:
    case["case_id"] = case["eval_id"]
    case["source"] = "step1-gptoss-1"
    if case["eval_id"] in prompt_examples and not case.get("skipped"):
        case["skipped"] = "원문이 검색어 검증 프롬프트 예시"
    for index, query in enumerate(case["sub_queries"]):
        key = (case["eval_id"], index)
        if key in UNSAFE:
            query["gold_unsafe"], query["note"] = True, UNSAFE[key]
        elif key in EITHER:
            query["gold_unsafe"], query["note"] = None, EITHER[key]
        else:
            query["gold_unsafe"] = False
        used.add(key)
missing = (set(UNSAFE) | set(EITHER)) - used
assert not missing, missing

for number, (question, query, note) in enumerate(SYNTHETIC_REFINED, 1):
    cases.append({"case_id": f"SYN-R{number:02d}", "eval_id": None, "expected_intent": "FAQ", "question": question,
                  "previous_subject": "", "path": "refined", "source": "synthetic",
                  "sub_queries": [{"quote": question, "query": query, "gold_unsafe": True, "note": note}]})
for number, (question, parts) in enumerate(SYNTHETIC_COMPOUND, 1):
    cases.append({"case_id": f"SYN-C{number:02d}", "eval_id": None, "expected_intent": "FAQ", "question": question,
                  "previous_subject": "", "path": "compound", "source": "synthetic",
                  "sub_queries": [{"quote": quote, "query": query, "gold_unsafe": unsafe, **({"note": note} if note else {})}
                                  for quote, query, unsafe, note in parts]})

for number, (question, query, unsafe, note) in enumerate(CHECK_REFINED, 1):
    cases.append({"case_id": f"CHK-R{number:02d}", "eval_id": None, "expected_intent": "FAQ", "question": question,
                  "previous_subject": "", "path": "refined", "source": "prompt-check",
                  "sub_queries": [{"quote": question, "query": query, "gold_unsafe": unsafe, **({"note": note} if note else {})}]})
for number, (question, parts) in enumerate(CHECK_COMPOUND, 1):
    cases.append({"case_id": f"CHK-C{number:02d}", "eval_id": None, "expected_intent": "FAQ", "question": question,
                  "previous_subject": "", "path": "compound", "source": "prompt-check",
                  "sub_queries": [{"quote": quote, "query": query, "gold_unsafe": unsafe, **({"note": note} if note else {})}
                                  for quote, query, unsafe, note in parts]})

scored = [q for c in cases if not c.get("skipped") for q in c["sub_queries"]]
result = {
    "description": "TELME-175 3단계 검색어 검증 평가셋. 실제 입력은 1단계 gpt-oss 1회차 의도 분류 출력에서 앱 코드로 만들었고, "
                   "뜻을 일부러 바꾼 검색어(source=synthetic)를 더했다. skipped는 앱에서 모델을 부르지 않는 경우다. "
                   "정답은 사람 검토 전이다.",
    "counts": {"cases": len(cases), "skipped_cases": sum(bool(c.get("skipped")) for c in cases),
               "queries": len(scored), "gold_unsafe": sum(q["gold_unsafe"] is True for q in scored),
               "gold_safe": sum(q["gold_unsafe"] is False for q in scored),
               "gold_either": sum(q["gold_unsafe"] is None for q in scored)},
    "cases": cases,
}
OUT.write_text(json.dumps(result, ensure_ascii=False, indent=1), encoding="utf-8")
print(result["counts"])
