#!/usr/bin/env python3

"""후속 질문 추천 데이터 생성: 정책 연결표(4차)와 FAQ별 규칙 메타데이터를 앱 리소스 JSON으로 내보낸다

설계·검증 근거: docs/FOLLOWUP_RECOMMENDATION.md 8절. 연결표를 바꾸면 이 파일을 고치고 다시 실행한다.
연결표·대표 질문·규칙의 원본은 이 파일 하나이고, 생성된 JSON은 손으로 고치지 않는다.

출력
  - src/main/resources/suggested-question/policy-links.json: 정책별 연결(순서 = 우선순위), 대표 질문, 매장 버튼 문장
  - src/main/resources/suggested-question/faq-rules.json: 문제 상황(TROUBLE) FAQ, 자격 조건 불가 FAQ, 단말 정책 FAQ의 상황
  - src/test/resources/suggested-question/expected-recommendations.json: FAQ 1,150개별 기대 추천(코드 결과 비교용)
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SRC = ROOT / "scripts/data/faq_full_1150.json"
MAIN_OUT = ROOT / "src/main/resources/suggested-question"
TEST_OUT = ROOT / "src/test/resources/suggested-question"

MAX_RECOMMENDATIONS = 2

# 매장 버튼: 기준 FAQ 답변에 이 단어가 있으면 매장 찾기 버튼을 첫 자리에 붙인다(연결표 추천 최대 2개와 별도)
STORE_MENTION = "매장"
# 매장에 갈 필요가 없다는 표현("매장 방문 없이", "매장에 가지 않아도", "매장이 아니라", "매장을 방문하지 않아도",
# "매장에 갈 필요가 없습니다", "매장 방문은 필요하지 않습니다")은 매장 언급으로 보지 않는다. 관리자 FAQ에도 적용되므로
# 데이터에 없는 표현까지 넓게 잡되, "매장에서 확인하지 않으면"처럼 매장과 무관한 부정은 걸리지 않도록 매장 바로 뒤의
# 동사는 방문/가/갈/하로 시작할 때만 허용한다(앞에 꼭/굳이/직접/반드시는 허용). "않", "안 가"는 "~해도 된다"는
# 허용 뜻일 때만 잡는다. "매장에 가지 않으면 처리할 수 없습니다", "매장 방문이 필수입니다"는 방문이 필요하다는 뜻이라
# 칩을 붙여야 한다. 앱(SuggestedQuestionRecommender)이 이 정규식을 JSON에서 그대로 읽어 쓴다
STORE_NEGATION = (r"매장(?:\s*방문)?\s*(?:을|에|이|은|는)?\s*(?:(?:꼭|굳이|직접|반드시)\s*)?"
                  r"(?:(?:방문|가|갈|하)[가-힣]{0,3}\s*)?"
                  r"(?:없이|아니라|아니고|않(?:아도|으셔도|고도)|안\s*가(?:도|셔도)"
                  r"|필요\s*(?:가|는)?\s*없|필요하지\s*않(?:습니다|아요|다)"
                  r"|필수\s*(?:는|가)?\s*(?:아니|아닙|아님)|불필요)")
# 카테고리별 버튼 문장. 누르면 라우터가 매장 찾기 + 업무(serviceType)로 분류해야 한다.
# "근처"를 넣으면 라우터가 지역 이름으로 받아 되묻기 대신 지역 검색을 하므로 넣지 않는다.
# 업무 표현은 RuleBasedRoutingFallback의 업무 규칙(유심.*재발급, 신규.*개통, 번호.*이동, 명의.*변경)에 맞춘다
# 다른 추천 버튼(실제 FAQ 질문)과 같은 존댓말로 쓴다
STORE_QUESTIONS = {
    "USIM": "유심 재발급 가능한 매장을 알려주세요.",
    "SUBSCRIBE": "신규 개통 가능한 매장을 알려주세요.",
    "PORTING": "번호이동 가능한 매장을 알려주세요.",
    "NAME_CHANGE": "명의변경 가능한 매장을 알려주세요.",
}
DEFAULT_STORE_QUESTION = "가까운 매장을 알려주세요."

POLICY_TITLE = {
    "BILLING-01": "요금제 변경 주기와 적용 시점", "BILLING-02": "청구서 발송과 납부 기한", "BILLING-03": "미납 시 이용 제한",
    "BILLING-04": "소액결제 한도", "BILLING-05": "납부 수단", "BILLING-06": "이중 납부 환불", "BILLING-07": "청구서 수령 방법",
    "PLAN-01": "요금제 구성", "PLAN-02": "데이터 제공량과 속도 제한", "PLAN-03": "데이터 이월과 선물하기",
    "PLAN-04": "청소년·시니어 요금제 자격", "PLAN-05": "요금제별 월정액과 기본 데이터",
    "USIM-01": "유심 재발급 비용과 소요 시간", "USIM-02": "유심 재발급 구비 서류", "USIM-03": "eSIM 지원", "USIM-04": "유심 분실 시 처리",
    "SUBSCRIBE-01": "신규 가입 구비 서류", "SUBSCRIBE-02": "미성년자 가입 요건", "SUBSCRIBE-03": "개통 소요 시간",
    "SUBSCRIBE-04": "회선 보유 한도",
    "PORTING-01": "번호이동 소요 시간", "PORTING-02": "번호이동 구비 서류", "PORTING-03": "번호이동 제한 조건",
    "PORTING-04": "번호이동 철회",
    "NAME_CHANGE-01": "명의변경 구비 서류", "NAME_CHANGE-02": "명의변경 처리 시간과 비용", "NAME_CHANGE-03": "명의변경 제한 조건",
    "TERMINATE-01": "해지 방법과 처리 시점", "TERMINATE-02": "약정 위약금", "TERMINATE-03": "단말 할부금 정산",
    "TERMINATE-04": "일시 정지",
    "DEVICE-01": "단말 할부 기간과 이자", "DEVICE-02": "기기 변경 조건", "DEVICE-03": "단말 분실·파손 처리",
    "DEVICE-04": "단말 반품·교환",
    "ROAMING-01": "로밍 요금제", "ROAMING-02": "로밍 신청 방법과 적용 시점", "ROAMING-03": "로밍 요금 자동 차단",
    "ROAMING-04": "로밍 부가 기능", "ROAMING-05": "로밍 이용 확인과 현지 설정",
    "SERVICE-01": "부가서비스 가입·해지", "SERVICE-02": "멤버십 등급과 혜택", "SERVICE-03": "매장 운영 시간",
    "SERVICE-04": "명의도용 신고", "SERVICE-05": "증명서 발급",
}

# 정책별 대표 질문(slot_id). 상황이 섞이지 않은 실제 FAQ 질문이라 누르면 그 FAQ가 근거로 나온다.
# "매장", "창구", "어디서"가 들어가면 라우터가 매장 찾기로 보내고, 금액을 물으면(비율만 있는 FAQ) LLM이 답하지 않는다.
# 그래서 실제 채팅 경로 확인(check_suggested_questions_live.py representatives)에서 실패한 3개를 같은 정책의 다른 질문으로 바꿨다
#   ROAMING-0012 "로밍 신청 창구를 알려주세요." → ROAMING-0022
#   SERVICE-0023 "매장 방문은 언제 가능한가요?" → SERVICE-0058
#   TERMINATE-0010 "약정 중간에 해지하면 위약금이 얼마나 나오나요?" → TERMINATE-0034
REPRESENTATIVE = {
    "BILLING-01": "BILLING-0015", "BILLING-02": "BILLING-0016", "BILLING-03": "BILLING-0045",
    "BILLING-05": "BILLING-0012", "BILLING-07": "BILLING-0021",
    "PLAN-01": "PLAN-0041", "PLAN-02": "PLAN-0012", "PLAN-03": "PLAN-0018", "PLAN-04": "PLAN-0014",
    "PLAN-05": "PLAN-0045",
    "USIM-01": "USIM-0017", "USIM-02": "USIM-0010", "USIM-04": "USIM-0036",
    "SUBSCRIBE-01": "SUBSCRIBE-0037", "SUBSCRIBE-02": "SUBSCRIBE-0014", "SUBSCRIBE-03": "SUBSCRIBE-0011",
    "PORTING-01": "PORTING-0033", "PORTING-02": "PORTING-0010", "PORTING-03": "PORTING-0011",
    "PORTING-04": "PORTING-0036",
    "NAME_CHANGE-01": "NAME_CHANGE-0007", "NAME_CHANGE-02": "NAME_CHANGE-0014", "NAME_CHANGE-03": "NAME_CHANGE-0009",
    "TERMINATE-01": "TERMINATE-0057", "TERMINATE-02": "TERMINATE-0034", "TERMINATE-03": "TERMINATE-0011",
    "TERMINATE-04": "TERMINATE-0036",
    "DEVICE-01": "DEVICE-0037", "DEVICE-02": "DEVICE-0010", "DEVICE-03": "DEVICE-0015", "DEVICE-04": "DEVICE-0040",
    "ROAMING-01": "ROAMING-0026", "ROAMING-02": "ROAMING-0022", "ROAMING-03": "ROAMING-0008",
    "ROAMING-05": "ROAMING-0025",
    "SERVICE-03": "SERVICE-0058",
}

# 연결 종류: NEXT 같은 업무의 다음 단계, ALTERNATIVE 대안, INFO 관련 정보
NEXT, ALTERNATIVE, INFO = "NEXT", "ALTERNATIVE", "INFO"
REPAIR = ["침수", "액정 파손"]


def link(to: str, kind: str = NEXT, condition: dict | None = None) -> dict:
    out = {"to": to, "kind": kind}
    if condition:
        out["condition"] = condition
    return out


STORE_WORD = {"questionContains": "매장"}

# 정책 → 이어서 물을 만한 정책. 앞이 우선이다. 4차 연결표(독립 검증 62/67 = 92.5%)
LINKS = {
    "BILLING-01": [link("PLAN-05", ALTERNATIVE), link("PLAN-01", ALTERNATIVE)],
    "BILLING-02": [link("BILLING-05"), link("BILLING-03", INFO), link("BILLING-07", INFO)],
    "BILLING-03": [link("BILLING-05")],
    "BILLING-04": [],
    "BILLING-05": [link("BILLING-02", INFO)],
    "BILLING-06": [],
    "BILLING-07": [link("BILLING-02", INFO), link("BILLING-05", INFO)],
    "PLAN-01": [link("PLAN-05", ALTERNATIVE), link("PLAN-02", INFO)],
    "PLAN-02": [link("PLAN-05", ALTERNATIVE), link("BILLING-01", INFO)],
    "PLAN-03": [link("PLAN-02", INFO), link("PLAN-05", ALTERNATIVE)],
    "PLAN-04": [link("PLAN-05", ALTERNATIVE), link("BILLING-01")],
    "PLAN-05": [link("BILLING-01"), link("PLAN-02", INFO)],
    "USIM-01": [link("USIM-02"), link("SERVICE-03", INFO)],
    "USIM-02": [link("USIM-01"), link("SERVICE-03", INFO)],
    "USIM-03": [link("USIM-01", ALTERNATIVE)],
    "USIM-04": [link("USIM-01"), link("USIM-02")],
    "SUBSCRIBE-01": [link("SUBSCRIBE-03"), link("SERVICE-03", INFO, STORE_WORD)],
    "SUBSCRIBE-02": [link("SUBSCRIBE-03")],
    "SUBSCRIBE-03": [link("SUBSCRIBE-01"), link("SERVICE-03", INFO, STORE_WORD)],
    "SUBSCRIBE-04": [link("SUBSCRIBE-01"), link("SUBSCRIBE-03")],
    "PORTING-01": [link("PORTING-02"), link("PORTING-04", INFO)],
    "PORTING-02": [link("PORTING-01"), link("PORTING-03", INFO)],
    "PORTING-03": [link("PORTING-02"), link("PORTING-01")],
    "PORTING-04": [],
    "NAME_CHANGE-01": [link("NAME_CHANGE-02"), link("NAME_CHANGE-03", INFO)],
    "NAME_CHANGE-02": [link("NAME_CHANGE-01"), link("NAME_CHANGE-03", INFO)],
    "NAME_CHANGE-03": [link("NAME_CHANGE-01"), link("NAME_CHANGE-02")],
    "TERMINATE-01": [link("TERMINATE-02", INFO), link("TERMINATE-03", INFO)],
    # 검증 때는 일시정지 대안에 "TROUBLE이 아닐 때만" 조건이 있었다. TROUBLE FAQ는 NEXT만 남기는
    # 공통 규칙이 같은 일을 하므로 조건을 두지 않는다(기대 추천 결과는 같다)
    "TERMINATE-02": [link("TERMINATE-03", INFO), link("TERMINATE-04", ALTERNATIVE)],
    "TERMINATE-03": [link("TERMINATE-02", INFO), link("TERMINATE-01")],
    "TERMINATE-04": [link("TERMINATE-01", ALTERNATIVE), link("TERMINATE-02", INFO)],
    "DEVICE-01": [link("DEVICE-02")],
    "DEVICE-02": [link("DEVICE-01"),
                  link("DEVICE-03", ALTERNATIVE, {"triggerIn": REPAIR}),
                  link("DEVICE-04", INFO, {"triggerIn": ["기기변경 희망"]})],
    "DEVICE-03": [link("DEVICE-02", ALTERNATIVE, {"triggerNotIn": ["분실"]}),
                  link("DEVICE-02", ALTERNATIVE, {"triggerIn": ["분실"]}),
                  link("USIM-01", ALTERNATIVE, {"triggerIn": ["분실"]})],
    "DEVICE-04": [link("DEVICE-03", ALTERNATIVE, {"triggerIn": REPAIR})],
    "ROAMING-01": [link("ROAMING-02"), link("ROAMING-03", INFO)],
    "ROAMING-02": [link("ROAMING-05"), link("ROAMING-01", INFO)],
    "ROAMING-03": [link("ROAMING-01", ALTERNATIVE)],
    "ROAMING-04": [link("ROAMING-02"), link("ROAMING-05", INFO)],
    "ROAMING-05": [link("ROAMING-03", INFO)],
    "SERVICE-01": [], "SERVICE-02": [], "SERVICE-03": [], "SERVICE-04": [], "SERVICE-05": [],
}

# 자격 조건 때문에 할 수 없다고 안내하는 FAQ. 사람이 확정한 16건이다(기간이 지나면 풀리는 경우는 제외).
# 이 FAQ에서는 NEXT 연결을 뺀다
ELIGIBILITY_BLOCKED = [
    "BILLING-0097", "DEVICE-0064", "NAME_CHANGE-0003", "NAME_CHANGE-0012", "NAME_CHANGE-0027", "NAME_CHANGE-0054",
    "NAME_CHANGE-0060", "NAME_CHANGE-0069", "NAME_CHANGE-0072", "PLAN-0084", "PLAN-0099", "PLAN-0114",
    "SERVICE-0037", "SUBSCRIBE-0022", "SUBSCRIBE-0080", "TERMINATE-0056",
]


def load_json(path: Path):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except OSError as exc:
        raise SystemExit(f"{path}: 파일을 읽을 수 없습니다: {exc}") from None
    except json.JSONDecodeError as exc:
        raise SystemExit(f"{path}: JSON 형식이 아닙니다: {exc}") from None


def validate(items: dict[str, dict]) -> None:
    errors = []
    policies = {x["policy_ref"] for x in items.values()}
    if set(LINKS) != policies:
        errors.append(f"연결표 정책과 FAQ 정책이 다릅니다: {sorted(set(LINKS) ^ policies)}")
    for pol, links in LINKS.items():
        for e in links:
            if e["to"] not in REPRESENTATIVE:
                errors.append(f"{pol} → {e['to']}: 대표 질문이 없습니다")
    for pol, slot in REPRESENTATIVE.items():
        if slot not in items or items[slot]["policy_ref"] != pol:
            errors.append(f"대표 질문 {pol} = {slot}: FAQ가 없거나 정책이 다릅니다")
    for slot in ELIGIBILITY_BLOCKED:
        if slot not in items:
            errors.append(f"자격 조건 불가 {slot}: FAQ가 없습니다")
    if errors:
        raise SystemExit("\n".join(errors))


def matches(condition: dict | None, faq: dict) -> bool:
    if not condition:
        return True
    if "questionContains" in condition:
        return condition["questionContains"] in faq["question"]
    if "triggerIn" in condition:
        return faq.get("trigger") in condition["triggerIn"]
    if "triggerNotIn" in condition:
        return faq.get("trigger") not in condition["triggerNotIn"]
    raise SystemExit(f"알 수 없는 조건: {condition}")


def mentions_store(answer: str) -> bool:
    return STORE_MENTION in re.sub(STORE_NEGATION, "", answer)


def store_question(faq: dict) -> str | None:
    if not mentions_store(faq["answer"]):
        return None
    return STORE_QUESTIONS.get(faq["category"], DEFAULT_STORE_QUESTION)


# 앱 구현이 따라야 하는 기준 동작. 기대 추천 파일은 이 함수의 결과다
def recommend(faq: dict, items: dict[str, dict]) -> list[str]:
    blocked = faq["slot_id"] in ELIGIBILITY_BLOCKED
    trouble = faq["question_type"] == "TROUBLE"
    out: list[str] = []
    for e in LINKS[faq["policy_ref"]]:
        if not matches(e.get("condition"), faq):
            continue
        if blocked and e["kind"] == NEXT:
            continue
        if trouble and e["kind"] != NEXT:
            continue
        question = items[REPRESENTATIVE[e["to"]]]["question"]
        if question in out:
            continue
        out.append(question)
        if len(out) == MAX_RECOMMENDATIONS:
            break
    return out


def trigger_policies() -> set[str]:
    return {pol for pol, links in LINKS.items()
            if any("triggerIn" in e.get("condition", {}) or "triggerNotIn" in e.get("condition", {}) for e in links)}


def build(items: dict[str, dict]) -> tuple[dict, dict, dict]:
    used = sorted({e["to"] for links in LINKS.values() for e in links})
    policy_links = {
        "representativeQuestions": {
            pol: {"slotId": REPRESENTATIVE[pol], "question": items[REPRESENTATIVE[pol]]["question"]} for pol in used
        },
        "policies": {
            pol: {"title": POLICY_TITLE[pol], "links": links} for pol, links in LINKS.items()
        },
        "storeQuestions": {
            "mention": STORE_MENTION,
            "negation": STORE_NEGATION,
            "byCategory": STORE_QUESTIONS,
            "default": DEFAULT_STORE_QUESTION,
        },
    }
    tp = trigger_policies()
    faq_rules = {
        "troubleSlotIds": sorted(s for s, x in items.items() if x["question_type"] == "TROUBLE"),
        "eligibilityBlockedSlotIds": sorted(ELIGIBILITY_BLOCKED),
        # 상황 조건이 있는 연결을 가진 정책의 FAQ만 담는다
        "triggers": {s: x["trigger"] for s, x in sorted(items.items()) if x["policy_ref"] in tp},
    }
    # 테스트가 원본 JSON 없이 돌도록 기준 FAQ의 정책·카테고리·질문과 답변의 매장 언급 여부(부정 표현 제외)를 함께 담는다
    expected = {s: {"policyRef": x["policy_ref"], "category": x["category"], "question": x["question"],
                    "mentionsStore": mentions_store(x["answer"]),
                    "suggestions": recommend(x, items), "storeQuestion": store_question(x)}
                for s, x in sorted(items.items())}
    return policy_links, faq_rules, expected


def write(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


# FAQ 1,150개짜리 기대값은 FAQ 하나를 한 줄로 써서 파일 크기와 변경 줄 수를 줄인다
def write_one_per_line(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    lines = [f"  {json.dumps(k, ensure_ascii=False)}: {json.dumps(v, ensure_ascii=False)}" for k, v in data.items()]
    path.write_text("{\n" + ",\n".join(lines) + "\n}\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--src", type=Path, default=DEFAULT_SRC, help="FAQ 원본 JSON")
    parser.add_argument("--check", action="store_true", help="파일을 쓰지 않고 생성 결과가 커밋된 파일과 같은지만 확인")
    args = parser.parse_args()

    items = {x["slot_id"]: x for x in load_json(args.src)}
    validate(items)
    policy_links, faq_rules, expected = build(items)
    outputs = {
        MAIN_OUT / "policy-links.json": policy_links,
        MAIN_OUT / "faq-rules.json": faq_rules,
        TEST_OUT / "expected-recommendations.json": expected,
    }

    if args.check:
        stale = [p for p, d in outputs.items() if not p.exists() or load_json(p) != d]
        if stale:
            raise SystemExit("다시 생성해야 하는 파일: " + ", ".join(str(p.relative_to(ROOT)) for p in stale))
        print("생성 파일이 최신입니다")
        return

    for path, data in outputs.items():
        (write_one_per_line if path.name == "expected-recommendations.json" else write)(path, data)
    counts = [len(v["suggestions"]) for v in expected.values()]
    n_links = sum(len(v) for v in LINKS.values())
    print(f"연결 {n_links}개, 쓰인 대표 질문 {len(policy_links['representativeQuestions'])}개, "
          f"자격 조건 불가 {len(ELIGIBILITY_BLOCKED)}건, TROUBLE {len(faq_rules['troubleSlotIds'])}건")
    print(f"FAQ별 추천 수: 2개 {counts.count(2)}, 1개 {counts.count(1)}, 없음 {counts.count(0)}")
    store = [v for v in expected.values() if v["storeQuestion"]]
    by_question = {}
    for v in store:
        by_question[v["storeQuestion"]] = by_question.get(v["storeQuestion"], 0) + 1
    print(f"매장 버튼: {len(store)}건 (연결표 추천이 없던 FAQ {sum(1 for v in store if not v['suggestions'])}건) "
          + ", ".join(f"{q} {n}" for q, n in by_question.items()))


if __name__ == "__main__":
    main()
