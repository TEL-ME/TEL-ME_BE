#!/usr/bin/env python3
"""FAQ 밖의 서비스 데이터로 만든 답변을 판정하기 위한 근거와 앱 고정 문구.

요금제 목록·추천 답변은 FAQ 검색 없이 요금제 표(plans)로 만든다(PlanAnswerProvider). 실행 기록(pipelineTrace)에
planAnswer 단계가 있는 턴에는 캡처 당시 요금제 표를 근거로 함께 넘긴다. 앱이 그대로 내보내는 안내·대체 문구는
FAQ 정책 주장이 아니라서 근거성에서 빼고, 처리 실패 대체 문구는 질문을 처리한 것으로 보지 않는다.
문구는 Java 소스와 글자 그대로 같아야 하고, 테스트가 소스에 그대로 있는지 확인한다.
"""
import re

PLAN_SOURCE_ID = "SERVICE:PLAN_TABLE"
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
    "저는 LG U+ 통신 고객센터 AI 상담사 무러바라예요 😊 통신 서비스와 매장 정보를 안내해 드려요.",  # ChatPersona
    "요금제, 청구·납부, 유심, 로밍 등 통신 서비스와 매장 관련 질문을 도와드려요 😊 궁금한 내용을 말씀해 주세요.",
)

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


def attach_service_sources(capture):
    """요금제 답변 경로를 탄 턴에 요금제 표 근거를 붙인다(capture를 바꾼다).
    실행 기록이 없는 예전 캡처는 그대로 두고, 기록은 있는데 요금제 표가 없으면 판정이 틀어지므로 멈춘다."""
    data = capture.get("serviceData") or {}
    for case in capture["cases"]:
        for turn in case["turns"]:
            if not plan_answer_type(turn):
                continue
            if not data.get("plans"):
                raise ValueError("요금제 답변 턴이 있는데 캡처에 요금제 표(serviceData.plans)가 없다. "
                                 "backfill_capture_service_data로 채운다")
            turn["serviceSources"] = [plan_table_source(data["plans"], data.get("planEligibility"))]
    return capture
