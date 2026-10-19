package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChatTopicClarificationTest {
    @Test
    @DisplayName("생성 경로의 공통 주제 확인 문구를 종료 요약에서도 같은 기준으로 인식한다")
    void 공통_문구를_주제_확인으로_인식한다() {
        assertThat(ChatTopicClarification.isMessage(ChatTopicClarification.MESSAGE)).isTrue();
        assertThat(ChatEndSummarySourceText.resolveSummaryMessageType(
                "ANSWER", null, ChatTopicClarification.MESSAGE)).isEqualTo("CLARIFICATION");
    }

    @Test
    @DisplayName("앞에 인사가 붙어도 주제 확인 문구를 인식한다")
    void 인사와_함께_나온_주제_확인을_인식한다() {
        assertThat(ChatTopicClarification.isMessage("안녕하세요! " + ChatTopicClarification.MESSAGE)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "요금제를 알려주세요.", "상품이나 상담 주제를 조금 더 구체적으로 알려주세요."})
    @DisplayName("일반 안내와 주제 확인 문구의 일부만으로는 주제 확인을 판정하지 않는다")
    void 공통_문구가_아닌_안내는_인정하지_않는다(String content) {
        assertThat(ChatTopicClarification.isMessage(content)).isFalse();
    }

    @Test
    @DisplayName("사용자 원문의 공통 문구 인용이나 일반 근거 답변을 확인 요청으로 바꾸지 않는다")
    void 역할과_근거가_다른_문장을_확인_요청으로_바꾸지_않는다() {
        assertThat(ChatEndSummarySourceText.resolveSummaryMessageType(
                "QUESTION", null, ChatTopicClarification.MESSAGE)).isEqualTo("QUESTION");
        assertThat(ChatEndSummarySourceText.resolveSummaryMessageType(
                "ANSWER", "GROUNDED", ChatTopicClarification.MESSAGE)).isEqualTo("ANSWER");
        assertThat(ChatTopicClarification.isMessage("‘" + ChatTopicClarification.MESSAGE + "’ 문의입니다.")).isFalse();
    }
}
