package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatSummaryNormalizerTest {

    @Test
    void removesTagsAndMarkdownWhileKeepingSummaryContent() {
        String generated = """
                <response>
                ### **요구 사항**
                - 가족 결합 휴대폰 할인 금액 확인
                1. 인터넷과 휴대폰 결합 할인 가능
                2. `강남점` 상담 요청
                </response>
                """;

        assertThat(ChatSummaryNormalizer.normalize(generated))
                .isEqualTo("요구 사항 가족 결합 휴대폰 할인 금액 확인 "
                        + "인터넷과 휴대폰 결합 할인 가능 강남점 상담 요청");
    }

    @Test
    void handlesUnclosedWrapperReturnedByModel() {
        assertThat(ChatSummaryNormalizer.normalize("""
                <previous_summary>
                - 고객은 일본 로밍 요금을 확인하려고 함
                - 구체적인 요금제가 아직 결정되지 않음
                """))
                .isEqualTo("고객은 일본 로밍 요금을 확인하려고 함 구체적인 요금제가 아직 결정되지 않음");
    }

    @Test
    void usesSummaryBlockInsteadOfDuplicatedPreviousSummary() {
        assertThat(ChatSummaryNormalizer.normalize("""
                <previous_summary>
                이전 요약
                </previous_summary>
                <summary>
                **갱신된 요약**
                </summary>
                """))
                .isEqualTo("갱신된 요약");
    }

    @Test
    void rejectsOutputThatFabricatesConversationData() {
        assertThat(ChatSummaryNormalizer.normalize("""
                <previous_summary>
                고객은 일본 로밍 요금을 문의함
                </previous_summary>
                <conversation_data>
                - 고객(QUESTION): 하루에 몇 GB를 사용할 수 있나요?
                - 상담사(ANSWER): 하루 1GB를 제공합니다.
                </conversation_data>
                """)).isNull();
        assertThat(ChatSummaryNormalizer.normalize(
                "고객(QUESTION): 새로운 질문을 만들었습니다."
        )).isNull();
    }

    @Test
    void returnsNullForNullBlankOrFormattingOnlyOutput() {
        assertThat(ChatSummaryNormalizer.normalize(null)).isNull();
        assertThat(ChatSummaryNormalizer.normalize("   ")).isNull();
        assertThat(ChatSummaryNormalizer.normalize("<summary>```</summary>")).isNull();
    }

    @Test
    void preservesPlainTextSummary() {
        assertThat(ChatSummaryNormalizer.normalize(
                "고객은 휴대폰 두 대의 가족 결합 할인을 확인하려고 함."
        )).isEqualTo("고객은 휴대폰 두 대의 가족 결합 할인을 확인하려고 함.");
    }
}
