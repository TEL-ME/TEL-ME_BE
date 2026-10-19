package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatEndSummarySourceTextTest {
    @Test
    @DisplayName("기간 대상과 정정값이 문장이나 줄바꿈으로 나뉘어도 원문을 함께 추출한다")
    void 분리된_기간_정정의_대상과_값을_보존한다() {
        assertThat(ChatEndSummarySourceText.extractPeriodQuotes(
                "가입 기간을 정정할게요. 18개월이 아니라 24개월입니다."))
                .containsExactly("가입 기간을 정정할게요. 18개월이 아니라 24개월입니다.");
        assertThat(ChatEndSummarySourceText.extractPeriodQuotes("약정 기간을 정정합니다.\n12개월이 아니라 2년입니다."))
                .containsExactly("약정 기간을 정정합니다. 12개월이 아니라 2년입니다.");
        assertThat(ChatEndSummarySourceText.extractPeriodQuotes("가입 18개월이라고 잘못 말했어요. 24개월입니다."))
                .containsExactly("가입 18개월이라고 잘못 말했어요. 24개월입니다.");
    }

    @Test
    @DisplayName("기간 대상 뒤 다른 사람의 값을 임의로 같은 정정으로 연결하지 않는다")
    void 문장_경계의_다른_대상을_합치지_않는다() {
        assertThat(ChatEndSummarySourceText.extractPeriodQuotes(
                "제 가입 기간을 정정할게요. 어머니는 24개월입니다."))
                .isEmpty();
        assertThat(ChatEndSummarySourceText.extractPeriodQuotes(
                "가입 기간을 확인합니다. 24개월 요금제의 비용은 7,700원입니다."))
                .isEmpty();
    }

    @Test
    @DisplayName("고정 주제 확인 요청만 확인 질문으로 구분하고 일반 답변은 유지한다")
    void 고정_확인_요청을_안내로_취급하지_않는다() {
        String question = "어떤 내용에 대한 질문인지 확인이 필요합니다. "
                + "상품이나 상담 주제를 조금 더 구체적으로 알려주세요.";

        assertThat(ChatEndSummarySourceText.resolveSummaryMessageType("ANSWER", null, question))
                .isEqualTo("CLARIFICATION");
        assertThat(ChatEndSummarySourceText.resolveSummaryMessageType("ANSWER", null, "방문 시 신분증이 필요합니다."))
                .isEqualTo("ANSWER");
        assertThat(ChatEndSummarySourceText.resolveSummaryMessageType("ANSWER", "GROUNDED", question))
                .isEqualTo("ANSWER");
    }

    @Test
    @DisplayName("빈 줄 없는 번호 제목도 문의별 경계로 사용하고 제목은 제외한다")
    void 번호_제목으로_여러_문의의_경계를_보존한다() {
        assertThat(ChatEndSummarySourceText.splitParagraphs(
                "1. 유심 교체 준비물\n신분증이 필요합니다.\n2. 로밍 이용 안내\n3일에 9,000원입니다."))
                .containsExactly("신분증이 필요합니다.", "3일에 9,000원입니다.");
    }

    @Test
    @DisplayName("무료·불가·금액 같은 짧은 정책을 제목으로 버리지 않는다")
    void 짧은_정책과_금액을_유지한다() {
        assertThat(ChatEndSummarySourceText.splitParagraphs("1. 무료\n\n2. 변경 불가\n\n3. 7,700원"))
                .containsExactly("1. 무료", "2. 변경 불가", "3. 7,700원");
    }

    @Test
    @DisplayName("예외 소제목은 제거해도 본문과 제한 조건을 하나의 후보로 남긴다")
    void 예외_조건을_본문에서_분리하지_않는다() {
        assertThat(ChatEndSummarySourceText.splitParagraphs(
                "1. 가입 안내\n가입할 수 있습니다.\n2. 예외\n만 65세 이상인 경우에만 가능합니다."))
                .containsExactly("가입할 수 있습니다.\n만 65세 이상인 경우에만 가능합니다.");
    }

    @Test
    @DisplayName("기간은 본인·가족과 부정·질문을 포함한 원문 문장으로 보존한다")
    void 기간의_대상과_원문_태도를_유지한다() {
        String quote = "저는 가입 12개월이고 어머니는 18개월인데 각각 해지 비용을 알려주세요.";

        assertThat(ChatEndSummarySourceText.extractPeriodQuotes(quote)).containsExactly(quote);
        assertThat(ChatEndSummarySourceText.extractPeriodQuotes("12개월 약정이 아니라 18개월 약정인가요?"))
                .containsExactly("12개월 약정이 아니라 18개월 약정인가요?");
        assertThat(ChatEndSummarySourceText.extractPeriodQuotes("3일 로밍은 얼마인가요?")).isEmpty();
    }

    @Test
    @DisplayName("준비물과 제목을 제외하고 실제 미확인 질문만 남긴다")
    void 복합_확인_질문의_안내_중복을_제거한다() {
        assertThat(ChatEndSummarySourceText.extractClarificationQuestion(
                "1. 준비물\n신분증이 필요합니다.\n\n2. 매장 위치\n어느 지역인가요? 역 이름을 알려주세요."))
                .isEqualTo("어느 지역인가요? 역 이름을 알려주세요.");
        assertThat(ChatEndSummarySourceText.extractClarificationQuestion("가입 시점 확인 필요"))
                .isEqualTo("가입 시점 확인 필요");
    }

    @Test
    @DisplayName("단어 일부가 아닌 완전한 문장 구간이 반복될 때만 중복으로 본다")
    void 문장_일부나_고유_조건을_중복으로_버리지_않는다() {
        assertThat(ChatEndSummarySourceText.containsSentences(
                "신분증이 필요합니다. 추가 서류는 안내하지 않았습니다.", "신분증이 필요합니다.")).isTrue();
        assertThat(ChatEndSummarySourceText.containsSentences(
                "신분증이 필요합니다. 추가 서류는 안내하지 않았습니다.", "신분증")).isFalse();
        assertThat(ChatEndSummarySourceText.containsSentences(
                "신분증이 필요합니다.", "신분증이 필요합니다. 대리인은 추가 서류가 필요합니다.")).isFalse();
    }
}
