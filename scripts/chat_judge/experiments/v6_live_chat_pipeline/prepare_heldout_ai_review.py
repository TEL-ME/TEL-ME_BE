"""Create an AI-adjudicated copy of the V6 human-review packet."""

from pathlib import Path
import re


ROOT = Path(__file__).resolve().parents[4]
EXPERIMENT = ROOT / "docs/chat-judge/experiments/V6-live-chat-pipeline"
SOURCE = EXPERIMENT / "20261005-heldout40-human-review.md"
OUTPUT = EXPERIMENT / "20261005-heldout40-ai-review.md"

# Labels are an assistant's review of the question, reference FAQ, stored answer,
# and the evidence actually supplied to generation. They are not human gold labels.
JUDGMENTS = {
    "V6H-001": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "BILLING-0087은 납부 기한을 기준으로 정지 시점을 정하고, BILLING-0059는 청구서를 못 받아도 같은 기준이 적용된다고 설명합니다. 기한 내 납부하면 정지를 막을 수 있다는 내용도 근거와 맞습니다."),
    "V6H-002": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "BILLING-0084가 모바일과 이메일 청구서는 무료라고 명시합니다. 답변도 별도 요금이 없다고 정확히 안내합니다."),
    "V6H-003": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "BILLING-0155는 청구서 발행 전후가 같고 다음 날 00:00부터 적용된다고 합니다. 월 사용료를 일할 계산한다는 설명은 BILLING-0127이 뒷받침합니다. 한 달에 한 번이라는 내용은 질문에 필요한 답이 아닙니다."),
    "V6H-004": ("NOT_APPLICABLE", ["MISSED"], "APPROPRIATE", "INSUFFICIENT",
                 "실제 RAG 근거가 없어 답변을 보류한 것은 안전합니다. 하지만 참고 FAQ에는 요금제가 9가지이고 15,000원부터 105,000원까지라고 나옵니다. 검색 누락으로 질문에 답하지 못했습니다."),
    "V6H-005": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "PLAN-0081은 알뜰 요금제로 세이브와 미니 두 가지를 제시합니다. 답변은 근거에 없는 가격이나 절감액을 덧붙이지 않고 두 요금제를 추천합니다."),
    "V6H-006": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "답변에 나온 네 요금제 이름이 PLAN-0116과 일치합니다."),
    "V6H-007": ("UNSUPPORTED", ["COMPLETE"], "SHOULD_ABSTAIN", "ENOUGH",
                 "근거는 침수 단말이 교환이 아니라 수리 대상이라고 합니다. 제조사 서비스센터를 방문하라는 추가 안내는 FAQ에 없으므로 근거 없는 사실 안내가 포함됐습니다."),
    "V6H-008": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "DEVICE-0130은 할부금 완납 또는 새 할부로 승계하는 방법과 개통 후 6개월이 지나야 한다는 조건을 모두 뒷받침합니다."),
    "V6H-009": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "DEVICE-0117은 두 기간의 연 수수료율이 5.9%로 같고 30개월의 총 수수료가 더 많다고 설명합니다. 구매 금액이 주어지지 않아 정확한 원화 차액을 안내할 수 없다는 답변도 타당합니다."),
    "V6H-010": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "SUBSCRIBE-0067은 온라인 신청은 다음 날 개통되고 당일 개통은 매장에서 가능하다고 명시합니다."),
    "V6H-011": ("UNSUPPORTED", ["MISSED"], "SHOULD_ABSTAIN", "ENOUGH",
                 "FAQ는 만 18세 이하까지 법정대리인 서류가 필요하다고 합니다. 질문은 만 18세 생일이 지난 경우이므로, 서류가 필요 없다는 답변은 근거와 맞지 않습니다."),
    "V6H-012": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "실제 전달된 SUBSCRIBE-0088의 질문이 외국인의 추가 회선 한도를 직접 묻고, 답변은 성인 1인당 최대 5회선 및 이미 5회선이면 추가 개통이 어렵다고 안내합니다. 생성 답변의 외국인 적용 표현은 질문과 이 근거의 문맥에 부합합니다. FAQ 전체에도 SUBSCRIBE-0108이 외국인과 내국인의 성인 한도가 같고 1인당 최대 5회선이라고 명시하지만, 이 FAQ는 해당 실행에서 실제 검색 근거로 전달되지는 않았습니다."),
    "V6H-013": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "PORTING-0081은 평균 2시간, 최대 24시간이 걸릴 수 있고 24시간이 지나면 고객센터에 문의하라고 합니다. 답변이 이 기준을 정확히 전달합니다."),
    "V6H-014": ("NOT_APPLICABLE", ["MISSED"], "APPROPRIATE", "INSUFFICIENT",
                 "검색된 로밍 FAQ는 번호이동 질문과 무관합니다. 답변을 보류한 것은 실제 근거 기준으로 안전하지만, 참고 FAQ에는 24시간 처리 기준이 있어 검색 단계에서 놓친 사례입니다."),
    "V6H-015": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "PORTING-0111은 번호이동은 개통 후 3개월이 지나야 가능하다고 하며, 개통 두 달이라는 상황에도 맞습니다. 명의 변경 예외는 질문에 없어 답하지 않아도 됩니다."),
    "V6H-016": ("NOT_APPLICABLE", ["MISSED"], "OVER_REFUSAL", "ENOUGH",
                 "관련 FAQ 세 건에 24개월 약정과 시점별 할인 반환금 100% 또는 50%가 명시되어 있는데도 답변을 거절했습니다."),
    "V6H-017": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "TERMINATE-0043은 남은 할부금을 한 번에 내거나 해지 후에도 나눠 낼 수 있다고 직접 설명합니다."),
    "V6H-018": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "TERMINATE-0033은 매장, 고객센터, 홈페이지 신청과 즉시 처리, 해지월 요금의 일할 계산을 뒷받침합니다."),
    "V6H-019": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "USIM-0035은 매장 또는 온라인 전환 신청, 2,750원 발급 비용, 지원 단말 조건을 모두 뒷받침합니다."),
    "V6H-020": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "USIM-0054는 본인 신분증이 필요하고 신분증 없이는 발급이 어렵다고 명시합니다."),
    "V6H-021": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "USIM-0065와 USIM-0045는 매장에서 바로 받을 수 있다고 설명합니다."),
    "V6H-022": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "제공된 명의 변경 FAQ는 약 30분 내 처리되고 미납 요금이 있으면 지연될 수 있다는 내용을 뒷받침합니다."),
    "V6H-023": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "NAME_CHANGE-0017은 수수료가 없고 약 30분 걸린다고 직접 설명합니다."),
    "V6H-024": ("UNSUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "실제 전달된 NAME_CHANGE-0042와 NAME_CHANGE-0018은 할부금이 남은 경우 양수인의 신용 심사를 통과해야 한다고만 안내합니다. 저장 답변의 '할부금을 모두 정산하거나'는 실제 전달 근거에 없으므로 실행 맥락 기준으로 근거가 부족합니다. FAQ 전체에는 완납 후 심사 없이 명의변경할 수 있다는 NAME_CHANGE-0078이 있지만, 이 실행의 검색 결과에는 포함되지 않았습니다. 질문 자체는 근거가 있는 신용 심사 조건으로 답할 수 있으므로 질문 충족도와 답변 가능성은 별도로 평가합니다."),
    "V6H-025": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "ROAMING-0002와 관련 FAQ는 신청 후 1시간 이내 적용된다고 직접 뒷받침합니다."),
    "V6H-026": ("NOT_APPLICABLE", ["MISSED"], "NOT_APPLICABLE", "INSUFFICIENT",
                 "로밍 신청 방법을 묻는 질문에 매장 위치를 찾기 위한 지역을 되물었습니다. 답변은 로밍 신청 질문에 응답하지 않았고, 실제 전달된 근거도 없습니다. 이는 답변 불가나 근거성 문제가 아니라 매장 의도로 잘못 분류해 생긴 무관한 되묻기입니다."),
    "V6H-027": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "ROAMING-0022는 홈페이지나 고객센터 신청, 출국 전 신청 권장, 현지 도착 후 신청 가능을 뒷받침합니다. 1시간 이내 적용도 함께 검색된 다른 로밍 FAQ에 있습니다."),
    "V6H-028": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "SERVICE-0034는 조사에서 명의도용이 확인되면 해당 요금이 전액 취소된다고 직접 설명합니다."),
    "V6H-029": ("UNSUPPORTED", ["PARTIAL"], "SHOULD_ABSTAIN", "ENOUGH",
                 "SERVICE-0044는 조사에 3영업일 이내가 걸리고 주말과 공휴일은 제외한다고 합니다. 하지만 답변은 그 기간이 지난 뒤에도 더 기다리라고 해, 근거가 없고 FAQ의 안내와 반대입니다."),
    "V6H-030": ("SUPPORTED", ["COMPLETE"], "NOT_APPLICABLE", "ENOUGH",
                 "SERVICE-0049는 명의도용 확인 후 요금이 취소되고 조사가 3영업일 이내 진행된다고 뒷받침합니다."),
    "V6H-031": ("UNSUPPORTED", ["COMPLETE", "MISSED"], "SHOULD_ABSTAIN", "INSUFFICIENT",
                 "PLAN-0004는 청소년·시니어 요금제 연령과 생년월일 확인 신분증을 뒷받침합니다. 두 번째 답의 서류 목록은 검색 근거에 없고 참고 FAQ의 본인 신분증 및 대리인 서류 안내와도 다릅니다."),
    "V6H-032": ("SUPPORTED", ["COMPLETE", "MISSED"], "APPROPRIATE", "INSUFFICIENT",
                 "DEVICE-0010은 6개월 경과 조건과 할부 처리 방법을 뒷받침합니다. 두 번째 질문에 필요한 명의 이전 서류는 실제 검색 근거에 없으므로 답변이 보류한 것이 적절합니다."),
    "V6H-033": ("SUPPORTED", ["COMPLETE", "MISSED"], "APPROPRIATE", "INSUFFICIENT",
                 "USIM-0040은 해외에서 유심 분실 시 처리 절차를 뒷받침합니다. 가족 데이터 나눔 절차는 실제 검색 근거에 없어 답변이 그 부분을 보류한 것이 적절합니다."),
    "V6H-034": ("NOT_APPLICABLE", ["NOT_APPLICABLE"], "APPROPRIATE", "INSUFFICIENT",
                 "체스 조언은 서비스 범위 밖이고, 답변은 통신 관련 질문으로 안내하며 사실 주장을 하지 않습니다."),
    "V6H-035": ("NOT_APPLICABLE", ["NOT_APPLICABLE"], "APPROPRIATE", "INSUFFICIENT",
                 "여권 유효기간 안내는 통신 서비스 범위 밖이고, 답변은 사실을 지어내지 않고 보류합니다."),
    "V6H-036": ("NOT_APPLICABLE", ["NOT_APPLICABLE"], "APPROPRIATE", "INSUFFICIENT",
                 "반려견 관리 조언은 통신 서비스 범위 밖이고, 답변은 사실을 지어내지 않고 보류합니다."),
    "V6H-037": ("UNSUPPORTED", ["COMPLETE"], "SHOULD_ABSTAIN", "ENOUGH",
                 "BILLING-0026은 입금 전용 계좌라는 정의와 여러 납부 방법을 뒷받침합니다. 하지만 계좌이체를 주된 방식으로 설계했다는 내용은 없고, FAQ는 계좌이체·신용카드·간편결제를 함께 나열합니다."),
    "V6H-038": ("UNSUPPORTED", ["MISSED"], "SHOULD_ABSTAIN", "INSUFFICIENT",
                 "근거는 유심 재발급 비용 7,700원과 배송 기간만 말하며 배송비 포함 여부는 설명하지 않습니다. 답변은 7,700원에 배송비가 포함된 것처럼 말해 질문에 필요한 정책을 근거 없이 단정했습니다."),
    "V6H-039": ("UNSUPPORTED", ["PARTIAL"], "SHOULD_ABSTAIN", "INSUFFICIENT",
                 "유심 재발급 비용 7,700원과 매장 즉시 발급은 근거가 있습니다. 온라인 배송비가 무료라는 내용은 실제 RAG에 없어, 답변은 일부만 맞고 배송 정책을 과하게 단정합니다."),
    "V6H-040": ("NOT_APPLICABLE", ["MISSED", "MISSED"], "APPROPRIATE", "INSUFFICIENT",
                 "실제 RAG 근거가 없어 답변을 보류한 것은 안전합니다. 참고 FAQ에는 두 질문의 답이 모두 있지만 검색되지 않아 어느 쪽에도 답하지 못했습니다."),
}

PIPELINE_FAILURE_TYPES = {
    "V6H-026": "MISROUTED_CLARIFICATION",
}


def render_case(review_id: str, source: str) -> str:
    grounding, quality, abstention, answerability, reason = JUDGMENTS[review_id]
    pipeline_failure = PIPELINE_FAILURE_TYPES.get(review_id, "NONE")
    lines = [
        "### AI 판정 초안 (Codex)",
        f"- 별도 파이프라인 실패 유형: `{pipeline_failure}`",
        f"- 근거성 전체 판정: `{grounding}`",
        f"- 근거성 판정 근거: {reason}",
        "- 주장별 근거와 FAQ ID: 판정 근거에 적힌 ID가 답변을 뒷받침합니다. `UNSUPPORTED` 사유에 적힌 주장은 실제 RAG에서 뒷받침되지 않습니다.",
        "- 질문 충족도:",
    ]
    for index, outcome in enumerate(quality, start=1):
        lines.append(f"  - 기준 {index}: `{outcome}`")
    lines.extend([
        f"- 답변 불가 판정: `{abstention}`",
        f"- 실제 전달 근거 충분성: `{answerability}`",
        f"- 메모: {reason}",
        "",
        "---",
        "",
    ])
    return "\n".join(lines)


def main() -> None:
    source = SOURCE.read_text(encoding="utf-8").replace("\r\n", "\n")
    parts = re.split(r"(?m)^## (V6H-\d{3})\s*$", source)
    ids = parts[1::2]
    if len(ids) != 40 or set(ids) != set(JUDGMENTS):
        raise ValueError(f"Expected the 40 known review cases, got {len(ids)}")
    output = [
        "# V6 별도 40건 AI 판정본",
        "",
        "> 이 문서는 Codex의 판정 초안입니다. 사람 확정 라벨이 아니며, 수정된 Judge의 정확도를 증명하지 않습니다. 질문, 참고 FAQ, 실제 저장 답변, 생성에 전달된 실제 RAG 근거를 대조해 작성했습니다. `NEEDS_HUMAN_REVIEW`는 정책 해석을 유보한 사례입니다.",
        "",
        "별도 파이프라인 실패 유형은 답변의 근거성·질문 충족도와 별개로 라우팅 및 응답 흐름 오류를 기록합니다. `MISROUTED_CLARIFICATION`은 질문과 무관한 의도로 잘못 분류해 엉뚱한 확인 질문을 한 경우입니다. V6H-026에 적용했습니다. V6H-012는 외국인 회선 한도 FAQ와 실제 전달 근거를 확인해 `SUPPORTED`로 판정했고, V6H-024는 실제 검색에 포함되지 않은 완납 선택지를 답변이 덧붙인 근거성 문제로 분류했습니다.",
        "",
        "원문 검토표의 질문, 답변, 실제 근거는 그대로 두고 빈 판정 칸을 아래 AI 판정으로 채웠습니다. 원본 블라인드 표는 별도 파일로 보존합니다.",
        "",
    ]
    for review_id, body in zip(parts[1::2], parts[2::2]):
        if "### 사람 판정 기록" not in body:
            raise ValueError(f"Human review section missing for {review_id}")
        body = body.split("### 사람 판정 기록", 1)[0].rstrip() + "\n\n" + render_case(review_id, body)
        output.extend([f"## {review_id}", body])
    OUTPUT.write_text("\n".join(output), encoding="utf-8", newline="\n")
    print(f"Wrote {OUTPUT} ({len(ids)} cases)")


if __name__ == "__main__":
    main()
