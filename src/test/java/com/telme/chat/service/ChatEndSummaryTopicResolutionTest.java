package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.repository.ChatEndSummaryInquiryRepository.Classification;
import com.telme.chat.safety.OutputMaskContext;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChatEndSummaryTopicResolutionTest {
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "번호이동 알려줘 | 번호이동 서류 알려줘 | PORTING",
        "가입 문의드려요 | 신규 가입 서류 알려줘 | SUBSCRIBE",
        "요금이요 | 5G 요금제 알려줘 | PLAN",
        "요금이요 | 이번 달 청구서 알려줘 | BILLING",
        "명의변경이 궁금해요 | 명의변경 서류 알려줘 | NAME_CHANGE",
        "단말 문의요 | 기기변경 할부 조건 알려줘 | DEVICE",
        "부가서비스 안내 부탁드립니다 | 부가서비스 종류 알려줘 | SERVICE",
        "청구서 문의드립니다 | 청구서 납부 방법 알려줘 | BILLING",
        "개통에 대해 알고 싶어요 | 신규 개통 서류 알려줘 | SUBSCRIBE",
        "요금제에 대해서 알려주세요 | 5G 요금제 알려줘 | PLAN",
        "유심이요 | 유심 재발급 비용 알려줘 | USIM",
        "로밍 문의요 | 로밍 신청 방법 알려줘 | ROAMING",
        "해지요 | 해지 방법 알려줘 | TERMINATE",
        "매장 문의요 | 가까운 매장 위치 알려줘 | STORE",
        "이심에 대해 알려주세요 | 이심 발급 비용 알려줘 | USIM",
        "할부가 궁금해요 | 단말 할부 조건 알려줘 | DEVICE",
        "매장 문의요 | 매장 영업시간 알려줘 | SERVICE"
    })
    @DisplayName("모든 FAQ 분류와 매장의 일반적인 주제 표현은 같은 주제의 정상 답변으로 해결한다")
    void 일반_주제_표현을_구체화한_정상_답변으로_해결한다(String original, String followup, String category) {
        var input = createInput(original, followup, category, "GROUNDED");

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isTrue();
        assertThat(ChatEndSummaryPendingResolver.resolve(input, Set.of(1L))).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "번호이동 알려줘 | 어머니 번호이동 서류 알려줘 | PORTING",
        "번호이동 알려줘 | 두 번째 회선 번호이동 서류 알려줘 | PORTING",
        "가입 문의드려요 | 친구 신규 가입 서류 알려줘 | SUBSCRIBE",
        "요금이요 | 다른 회선의 5G 요금제 알려줘 | PLAN",
        "명의변경 알려줘 | 번호이동 서류 알려줘 | PORTING",
        "가입 문의드려요 | 해지 방법 알려줘 | TERMINATE",
        "부가서비스 알려줘 | 단말 할부 조건 알려줘 | DEVICE",
        "번호이동 알려줘 | 별도 문의로 번호이동 서류 알려줘 | PORTING",
        "번호이동 비용 알려줘 | 번호이동 서류 알려줘 | PORTING",
        "가입 자격이 궁금해요 | 신규 가입 서류 알려줘 | SUBSCRIBE",
        "해지 위약금 알려줘 | 해지 방법 알려줘 | TERMINATE",
        "요금과 유심 알려줘 | 5G 요금제 알려줘 | PLAN",
        "멤버십 알려줘 | 매장 영업시간 알려줘 | SERVICE",
        "부가서비스 알려줘 | 명의도용 신고 방법 알려줘 | SERVICE",
        "청구서 문의드립니다 | 요금제 변경 적용 시점 알려줘 | BILLING",
        "납부 알려줘 | 청구서 발급 방법 알려줘 | BILLING",
        "할부가 궁금해요 | 기기 분실 대처 방법 알려줘 | DEVICE",
        "매장 문의요 | 멤버십 혜택 알려줘 | SERVICE",
        "멤버십 알려줘 | 멤버십 말고 매장 영업시간 알려줘 | SERVICE",
        "부가서비스 알려줘 | 부가서비스 말고 멤버십 혜택 알려줘 | SERVICE",
        "요금이요 | 요금 말고 5G 요금제 종류 알려줘 | PLAN",
        "유심이요 | 가족 유심 재발급 비용 알려줘 | USIM",
        "유심이요 | 형 유심 재발급 비용 알려줘 | USIM",
        "유심이요 | 직장 동료 유심 재발급 비용 알려줘 | USIM",
        "요금이요 | 가족 5G 요금제 알려줘 | PLAN",
        "요금이요 | 형 5G 요금제 알려줘 | PLAN",
        "요금이요 | 동료 5G 요금제 알려줘 | PLAN",
        "유심이요 | 그럼 형 유심 재발급 비용 알려줘 | USIM",
        "요금이요 | 그리고 형 5G 요금제 알려줘 | PLAN"
    })
    @DisplayName("표현을 넓혀도 다른 대상·회선·주제와 원래 세부 질문은 해결로 처리하지 않는다")
    void 다른_문의와_세부_질문은_남긴다(String original, String followup, String category) {
        var input = createInput(original, followup, category, "GROUNDED");

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
        assertThat(ChatEndSummaryPendingResolver.resolve(input, Set.of(1L))).isNotEmpty();
    }

    @ParameterizedTest
    @CsvSource({"NO_EVIDENCE", "OUT_OF_SCOPE"})
    @DisplayName("구체화한 같은 주제라도 근거 있는 정상 답변이 없으면 남은 사항을 유지한다")
    void 주제_구체화_뒤_실패는_해결하지_않는다(String basis) {
        var input = createInput("번호이동 알려줘", "번호이동 서류 알려줘", "PORTING", basis);

        assertThat(ChatEndSummaryResolution.isResolved(input, 1)).isFalse();
    }

    private ChatEndSummaryData.Input createInput(String original, String followup, String category, String basis) {
        var messages = List.of(
                new ChatEndSummaryData.Message(1, 1, null, "USER", "QUESTION", original, null, "COMPLETED"),
                new ChatEndSummaryData.Message(2, 2, 1L, "ASSISTANT", "CLARIFICATION",
                        ChatTopicClarification.MESSAGE, null, "COMPLETED"),
                new ChatEndSummaryData.Message(3, 3, null, "USER", "QUESTION", followup, null, "COMPLETED"),
                new ChatEndSummaryData.Message(4, 4, 3L, "ASSISTANT", "ANSWER", "상담 안내 원문입니다.",
                        basis, "COMPLETED"));
        var classification = category.equals("STORE") ? new Classification("STORE", List.of())
                : new Classification("FAQ", List.of(category));
        return new ChatEndSummaryData.Input(1, 4, 1L, messages,
                List.of(new ChatEndSummaryData.Request(3, 1, followup, "DONE", List.of(), 102L)), Set.of(1L),
                List.of(), OutputMaskContext.empty(), Map.of(3L, classification, 4L, classification));
    }
}
