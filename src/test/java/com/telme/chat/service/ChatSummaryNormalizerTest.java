package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
        assertThat(ChatSummaryNormalizer.normalize("---")).isNull();
        assertThat(ChatSummaryNormalizer.normalize("***")).isNull();
        assertThat(ChatSummaryNormalizer.normalize("___")).isNull();
    }

    @Test
    void preservesPlainTextSummary() {
        assertThat(ChatSummaryNormalizer.normalize(
                "고객은 휴대폰 두 대의 가족 결합 할인을 확인하려고 함."
        )).isEqualTo("고객은 휴대폰 두 대의 가족 결합 할인을 확인하려고 함.");
    }

    @Test
    @DisplayName("문장 중간과 시작의 고정 민감정보 마스크를 보존한다")
    void 문장_중간과_시작의_마스크를_보존한다() {
        assertThat(ChatSummaryNormalizer.normalize("주민번호 *** 카드 *** 유심"))
                .isEqualTo("주민번호 *** 카드 *** 유심");
        assertThat(ChatSummaryNormalizer.normalize("*** 카드로 결제한 내역"))
                .isEqualTo("*** 카드로 결제한 내역");
    }

    @Test
    @DisplayName("마스킹을 보존하면서 강조·목록·링크 표시를 제거한다")
    void 마스크_주변의_마크다운만_제거한다() {
        assertThat(ChatSummaryNormalizer.normalize("""
                <summary>
                ### **납부 문의**
                - 카드 ***로 `결제`한 내역
                * 주민번호 *** 확인
                1. __신청__ 경로는 [홈페이지](https://example.com)
                **카드 *** 안내**
                </summary>
                """))
                .isEqualTo("납부 문의 카드 ***로 결제한 내역 주민번호 *** 확인 "
                        + "신청 경로는 홈페이지 카드 *** 안내");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "**카드 ***** | 카드 ***", "***** 카드** | *** 카드",
        "******* 카드 문의 | *** 카드 문의", "카드 ****** 문의 | 카드 ****** 문의"
    })
    @DisplayName("강조 기호가 마스크에 붙어도 고정 마스크를 유지하고 인접한 두 마스크는 합치지 않는다")
    void 강조와_붙은_마스크를_보존한다(String input, String expected) {
        assertThat(ChatSummaryNormalizer.normalize(input)).isEqualTo(expected);
    }
}
