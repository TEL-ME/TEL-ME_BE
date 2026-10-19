#!/usr/bin/env python3
"""FAQ 밖의 서비스 데이터로 만든 답변을 판정하기 위한 근거와 앱 고정 문구.

요금제 목록·추천 답변은 FAQ 검색 없이 요금제 표(plans)로 만든다(PlanAnswerProvider). 실행 기록(pipelineTrace)에
planAnswer 단계가 있는 턴에는 캡처 당시 요금제 표를 근거로 함께 넘긴다. 앱이 그대로 내보내는 안내·대체 문구는
FAQ 정책 주장이 아니라서 근거성에서 빼고, 처리 실패 대체 문구는 질문을 처리한 것으로 보지 않는다.
문구는 Java 소스와 글자 그대로 같아야 하고, 테스트가 소스에 그대로 있는지 확인한다.
"""
from datetime import datetime, timedelta, timezone
import re

PLAN_SOURCE_ID = "SERVICE:PLAN_TABLE"
MEMBER_SOURCE_ID = "SERVICE:MEMBER_DATA"
STORE_SOURCE_ID = "SERVICE:STORE_RESULTS"
RULE_SOURCE_ID = "SERVICE:RECOMMENDATION_RULE"
# 요금제 추천 규칙(PlanRecommender, MemberRecommendationAnswerer). 바뀌면 test_service_data가 Java 상수와 대조한다
RECOMMENDATION_MARGIN = 1.2
DOWNGRADE_MARGIN = 1.4
HEAVY_DATA_GB = 50
LIGHT_DATA_GB = 3
# 회원 정보로 답하는 요금제 답변 유형(PlanAnswerProvider). 추천은 요금제 표와 회원 사용량을 함께 쓴다
MEMBER_ANSWER_TYPES = {"MY_SUBSCRIPTION", "MY_PENALTY", "MY_USAGE", "RECOMMENDATION"}
PLAN_TABLE_ANSWER_TYPES = {"PLAN_CATALOG", "RECOMMENDATION"}
DAY_NAMES = {1: "월", 2: "화", 3: "수", 4: "목", 5: "금", 6: "토", 7: "일"}
NETWORK_LABELS = {"FIVE_G": "5G", "LTE": "LTE", "BUDGET": "알뜰"}
TARGET_LABELS = {"GENERAL": "일반", "YOUTH": "청소년", "SENIOR": "시니어"}

# 앱이 그대로 붙이는 서비스 안내(로그인·화면 이동·인사). 정책 사실이 아니라 근거성 판정에서 뺀다
SERVICE_GUIDANCE = (
    "로그인하시면 약정 기간과 해지할 때 내는 위약금을 확인해 드릴 수 있어요.",  # MemberPlanAnswerer
    "로그인하시면 가입하신 요금제를 확인해 드릴 수 있어요.",
    "로그인하시면 이번 달 사용량과 예상 요금을 확인해 드릴 수 있어요.",
    "요금제가 궁금하시면 아래에서 전체 요금제를 살펴보세요.",  # MemberPlanAnswerer, MemberRecommendationAnswerer
    "사용량을 알려주시면 맞는 요금제를 추천해 드릴 수 있어요.",  # MemberRecommendationAnswerer
    "가입한 요금제의 사용량이 쌓이면 더 정확히 추천해 드려요.",
    "로그인하고 요금제에 가입하시면 평소 사용량에 맞춰 더 정확히 추천해 드려요.",
    "혹시 이런 내용을 찾으셨나요?",  # ConsultChatProcessingService
    "저는 통신 서비스와 매장 관련 질문을 도와드려요. 요금제, 로밍, 유심 또는 매장에 대해 궁금한 내용을 알려주세요.",
    "저는 TELME 통신 고객센터 AI 상담사 무러바라예요 😊 통신 서비스와 매장 정보를 안내해 드려요.",  # ChatPersona
    "요금제, 청구·납부, 유심, 로밍 등 통신 서비스와 매장 관련 질문을 도와드려요 😊 궁금한 내용을 말씀해 주세요.",
)

# FAQ 검색 결과가 하나도 없을 때의 고정 안내(RagSearchResultAnswerGenerator). 답이 없는 질문에서는 지어내지 않았으므로
# 보류로 인정하고(LABEL_REVIEW.md D1), 그렇게 통과한 턴은 발견 사항(NO_SEARCH_RESULT_FALLBACK)으로 따로 센다
NO_SEARCH_RESULT = "관련 안내 정보를 찾지 못했습니다. 통신 서비스나 매장 관련 질문이라면 조금 더 구체적으로 알려주세요."

# 생성 모델이 근거로 답할 수 없을 때 그대로 내보내는 한 문장(AnswerPromptTemplates.NO_EVIDENCE_MESSAGE)
NO_EVIDENCE = "지금 확인할 수 있는 정보만으로는 정확하게 안내드리기 어려워요"

# 질문을 처리하지 못했을 때 앱이 내보내는 대체 문구. 보류나 범위 밖 안내가 아니라 처리 실패다
SYSTEM_FALLBACKS = (
    "현재 질문을 안전하게 나누어 처리하기 어려워요. 궁금한 내용을 하나씩 알려주세요.",  # QueryRoutingAnalysisProvider
    "질문은 한 번에 최대 3개까지 처리할 수 있습니다. 질문을 나누어 보내주세요.",
    "질문을 정확히 분류하기 어렵습니다. 궁금한 통신 서비스나 매장 정보를 조금 더 구체적으로 알려주세요.",
    "어떤 내용에 대한 질문인지 확인이 필요합니다. 상품이나 상담 주제를 조금 더 구체적으로 알려주세요.",  # ConsultTurnAnalysisAdapter
    "지역이 필요한 매장 찾기가 여러 개라 한 번에 확인하기 어려워요. 매장 찾기는 하나씩 나누어 보내주세요.",
    "현재 가까운 매장 정보를 바로 확인하기 어려워요. 잠시 후 다시 시도하거나 고객센터를 이용해 주세요.",
)


def compact(text):
    return re.sub(r"\s+", "", text or "")


_GUIDANCE = tuple(compact(sentence) for sentence in SERVICE_GUIDANCE)
_FALLBACKS = {compact(sentence) for sentence in SYSTEM_FALLBACKS}


def is_service_guidance(claim):
    """주장 전체가 앱 고정 안내 문장 하나 안에 들어가면 서비스 안내다. 짧은 조각은 우연히 겹칠 수 있어 보지 않는다."""
    text = compact(claim).rstrip(".")
    return len(text) >= 8 and any(text in sentence for sentence in _GUIDANCE)


def system_fallback(content):
    """답변 전체가 처리 실패 대체 문구면 그 문구를 돌려준다."""
    return compact(content) in _FALLBACKS


def login_guidance_only(content):
    """답변 전체가 로그인 안내를 포함한 앱 고정 안내 문장으로만 이루어졌는지."""
    sentences = [part for part in re.split(r"(?<=[.?!])\s+", (content or "").strip()) if part]
    return bool(sentences) and all(is_service_guidance(part) for part in sentences) and any(
        "로그인" in part for part in sentences)


def is_no_search_result(content):
    return compact(content) == compact(NO_SEARCH_RESULT)


def is_refusal_template(content):
    """답변 전체가 앱의 거절 고정 문장(검색 결과 0건 안내, 근거 부족 한 문장)인지. 끝 마침표는 보지 않는다."""
    text = compact(content).rstrip(".")
    return text in {compact(NO_SEARCH_RESULT).rstrip("."), compact(NO_EVIDENCE).rstrip(".")}


def _limit(unlimited, value, scale=1):
    return None if unlimited or value is None else value * scale


def recommendation_rule_source(member_data):
    """앱의 요금제 추천 규칙. '사용량에 알맞다' 같은 결론은 이 규칙으로 정해져 FAQ나 계정 데이터만으로는 판정할 수 없다.
    로그인 회원이면 지금 요금제가 지난 달 평균 사용량에 알맞은지도 규칙대로 계산해 적는다."""
    lines = ["앱의 요금제 추천 규칙",
             f"요금제가 사용량에 알맞다는 것은 데이터·통화·문자 제공량이 각각 평균 사용량의 {RECOMMENDATION_MARGIN}배 이상(무제한 포함)이라는 뜻이다.",
             f"추천은 알맞은 요금제 중 가장 싼 요금제다. 지금 요금제가 알맞으면 그대로 쓰라고 권하고, 더 싼 요금제는 {DOWNGRADE_MARGIN}배 여유가 있을 때만 권한다.",
             f"사용량을 모르면 데이터를 많이 쓰는 고객은 {HEAVY_DATA_GB}GB, 적게 쓰는 고객은 {LIGHT_DATA_GB}GB 이상 주는 가장 싼 요금제를 추천한다(나이에 맞는 요금제 중)."]
    plan = (((member_data or {}).get("subscription") or {}).get("subscription") or {}).get("currentPlan")
    demand = (member_data or {}).get("usageDemand")
    if plan and isinstance(demand, dict) and "dataMb" in demand:
        limits = {"데이터": (_limit(plan.get("unlimited"), plan.get("dataGb"), MB_PER_GB), demand["dataMb"]),
                  "통화": (_limit(plan.get("voiceUnlimited"), plan.get("voiceMinutes")), demand["voiceMinutes"]),
                  "문자": (_limit(plan.get("smsUnlimited"), plan.get("smsCount")), demand["smsCount"])}
        short = [name for name, (limit, used) in limits.items() if limit is not None and limit < used * RECOMMENDATION_MARGIN]
        lines.append(f"이 회원의 지금 요금제({plan.get('name')})는 지난 {demand.get('months')}개월 평균 사용량에 "
                     + ("알맞다." if not short else f"알맞지 않다({', '.join(short)} 부족)."))
    return {"sourceId": RULE_SOURCE_ID, "kind": "SERVICE_DATA", "answer": "\n".join(lines)}


def plan_answer_type(turn):
    return ((turn.get("pipelineTrace") or {}).get("planAnswer") or {}).get("type")


def _number(value):
    return f"{value:,}"


def _data(plan):
    if plan["is_unlimited"]:
        return "무제한"
    return f"{float(plan['data_gb']):g}GB"


def plan_table_source(plans, eligibility):
    """판매 중 요금제를 한 줄씩 적은 근거. 개수와 최저가처럼 표에서 바로 나오는 값도 줄로 적어 판정 모델이 세지 않게 한다."""
    active = [plan for plan in plans if plan["status"] == "ACTIVE"]
    lines = ["판매 중인 요금제 표(요금제 데이터)"]
    for plan in active:
        lines.append("{name}: {network} 요금제, {target} 대상, 월정액 {fee}원, 데이터 {data}, 통화 {voice}, 문자 {sms}".format(
            name=plan["name"], network=NETWORK_LABELS[plan["network_type"]], target=TARGET_LABELS[plan["target_group"]],
            fee=_number(plan["monthly_fee"]), data=_data(plan),
            voice="무제한" if plan["is_voice_unlimited"] else f"{plan['voice_minutes']}분",
            sms="무제한" if plan["is_sms_unlimited"] else f"{plan['sms_count']}건"))

    lines.append(f"위 {len(active)}종이 판매 중인 요금제 전부다. 목록에 없는 요금제는 판매하지 않는다.")

    def counts(rows):
        return ", ".join(f"{label} {sum(row['network_type'] == key for row in rows)}종"
                         for key, label in NETWORK_LABELS.items() if any(row["network_type"] == key for row in rows))
    general = [plan for plan in active if plan["target_group"] == "GENERAL"]
    lines.append(f"판매 중인 요금제는 총 {len(active)}종이다({counts(active)}).")
    lines.append(f"일반 요금제(청소년·시니어 제외)는 {len(general)}종이다({counts(general)}).")
    for key in ("YOUTH", "SENIOR"):
        rows = [plan for plan in active if plan["target_group"] == key]
        if rows:
            lines.append(f"{TARGET_LABELS[key]} 요금제는 {len(rows)}종이다({counts(rows)}).")
    for label, rows in (("일반 요금제", general), ("전체 요금제", active)):
        if rows:
            low = min(rows, key=lambda row: row["monthly_fee"])
            high = max(rows, key=lambda row: row["monthly_fee"])
            lines.append(f"{label} 중 가장 저렴한 요금제는 {low['name']}(월 {_number(low['monthly_fee'])}원)이고, "
                         f"가장 비싼 요금제는 {high['name']}(월 {_number(high['monthly_fee'])}원)이다.")
    if eligibility:
        lines.append(f"청소년 요금제는 만 {eligibility['youthMinAge']}~{eligibility['youthMaxAge']}세만 가입할 수 있다.")
        lines.append(f"시니어 요금제는 만 {eligibility['seniorMinAge']}세 이상만 가입할 수 있다.")
    return {"sourceId": PLAN_SOURCE_ID, "kind": "SERVICE_DATA", "answer": "\n".join(lines)}


KST = timezone(timedelta(hours=9))
MB_PER_GB = 1024  # 앱(PlanFormat, UsageGenerator)과 같은 환산
_INSTANT = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d+)?)?Z")
_MEGABYTE_KEYS = ("used", "limit", "remaining", "dataMb")


def _readable(path, value):
    """UTC 시각은 한국 시간으로, MB 값은 GB를 함께 적는다. 앱이 고객에게 보여 주는 단위와 맞춘다."""
    if isinstance(value, str) and _INSTANT.fullmatch(value):
        moment = datetime.fromisoformat(value.replace("Z", "+00:00")).astimezone(KST)
        return f"{value} (한국 시간 {moment:%Y-%m-%d %H:%M})"
    if isinstance(value, (int, float)) and not isinstance(value, bool) and (
            ".data." in f".{path}." and path.rsplit(".", 1)[-1] in _MEGABYTE_KEYS or path.endswith("dataMb")):
        return f"{value}MB ({value / MB_PER_GB:.1f}GB)"
    return value


def _flatten(value, path=""):
    if isinstance(value, dict):
        for key, child in value.items():
            yield from _flatten(child, f"{path}.{key}" if path else key)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            yield from _flatten(child, f"{path}[{index}]")
    elif value is not None:
        yield f"{path}: {_readable(path, value)}"


def member_data_source(member_data):
    """로그인 회원의 계정 데이터(가입 요금제, 약정·위약금, 이번 달 사용량과 예상 요금)를 한 줄에 값 하나씩 적은 근거.
    프로브가 대화 직전에 앱이 회원 답변에 쓰는 서비스를 불러 남긴 값이다."""
    lines = ["로그인한 회원의 계정 데이터(대화 직전 조회, 금액은 원, 시각은 UTC와 한국 시간, 데이터는 MB와 GB(1GB=1024MB)). "
             "usageDemand는 지난 달들의 한 달 평균 사용량이다"]
    for section in ("subscription", "terminationPenalty", "usage", "usageDemand"):
        lines.extend(f"{section}.{line}" for line in _flatten(member_data.get(section)))
    return {"sourceId": MEMBER_SOURCE_ID, "kind": "SERVICE_DATA", "answer": "\n".join(lines)}


def store_results_source(turn):
    """사용자에게 보여 준 매장 목록과 각 매장의 취급 업무·영업시간."""
    output = turn.get("outputMessage") or {}
    details = {row["storeId"]: row for row in turn.get("storeDetails", [])}
    context = output.get("storeSearchContext") or {}
    lines = [f"매장 검색 결과(검색 기준: {context.get('label') or '알 수 없음'})"]
    for store in output.get("storeResults") or []:
        detail = details.get(store.get("storeId"), {})
        hours = ", ".join(
            f"{DAY_NAMES.get(row['day_of_week'], row['day_of_week'])} "
            + ("휴무" if row.get("closed") else f"{str(row['open_time'])[:5]}~{str(row['close_time'])[:5]}")
            for row in detail.get("hours", []))
        lines.append(f"{store.get('name')}: 주소 {store.get('address')}, 전화 {store.get('phone')}, "
                     f"거리 {store.get('distanceMeters')}m, 취급 업무 {', '.join(detail.get('services', [])) or '없음'}, "
                     f"영업시간 {hours or '정보 없음'}")
    return {"sourceId": STORE_SOURCE_ID, "kind": "SERVICE_DATA", "answer": "\n".join(lines)}


def attach_service_sources(capture):
    """FAQ 밖 데이터로 만든 답변에 그 데이터를 근거로 붙인다(capture를 바꾼다).
    - 요금제 목록·추천: 요금제 표 / 로그인 회원의 내 정보·추천: 회원 계정 데이터 / 매장 결과: 보여 준 매장 목록
    평가 라벨의 서비스 데이터 인정 근거(답변 본문 없이 sourceId만 적은 것)도 같은 데이터로 채운다.
    실행 기록이 없는 예전 캡처는 그대로 두고, 기록은 있는데 요금제 표가 없으면 판정이 틀어지므로 멈춘다."""
    data = capture.get("serviceData") or {}
    for case in capture["cases"]:
        member = case.get("memberData") if case.get("actor") == "MEMBER" else None
        for turn in case["turns"]:
            available = {}
            if data.get("plans"):
                available[PLAN_SOURCE_ID] = plan_table_source(data["plans"], data.get("planEligibility"))
            if member:
                available[MEMBER_SOURCE_ID] = member_data_source(member)
            sources = []
            answer_type = plan_answer_type(turn)
            if answer_type in PLAN_TABLE_ANSWER_TYPES or answer_type and not member:
                if PLAN_SOURCE_ID not in available:
                    raise ValueError("요금제 답변 턴이 있는데 캡처에 요금제 표(serviceData.plans)가 없다. "
                                     "backfill_capture_service_data로 채운다")
                sources.append(available[PLAN_SOURCE_ID])
            if answer_type in MEMBER_ANSWER_TYPES and member:
                sources.append(available[MEMBER_SOURCE_ID])
            if answer_type == "RECOMMENDATION":
                sources.append(recommendation_rule_source(member))
            if (turn.get("outputMessage") or {}).get("storeResults"):
                sources.append(store_results_source(turn))
            if sources:
                turn["serviceSources"] = sources
            for group in turn["fixture"].get("qualityReferenceGroups", []):
                for alternative in group.get("alternatives", []):
                    if str(alternative.get("sourceId", "")).startswith("SERVICE:") and "answer" not in alternative:
                        if alternative["sourceId"] not in available:
                            raise ValueError(f"{case['caseId']}: 평가 라벨이 쓰는 {alternative['sourceId']}가 캡처에 없다")
                        alternative["answer"] = available[alternative["sourceId"]]["answer"]
    return capture
