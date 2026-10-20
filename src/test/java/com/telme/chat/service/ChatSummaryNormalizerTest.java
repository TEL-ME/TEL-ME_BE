package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.guard.ChatInputInspector;
import com.telme.chat.safety.ChatOutputBlockedException;
import com.telme.chat.safety.ChatOutputGuard;
import com.telme.chat.safety.OutputMaskContext;
import com.telme.chat.guard.MaskSpan;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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

        assertThat(ChatSummaryNormalizer.normalize(generated, OutputMaskContext.empty()))
                .isEqualTo("요구 사항 가족 결합 휴대폰 할인 금액 확인 "
                        + "인터넷과 휴대폰 결합 할인 가능 강남점 상담 요청");
    }

    @Test
    void handlesUnclosedWrapperReturnedByModel() {
        assertThat(ChatSummaryNormalizer.normalize("""
                <previous_summary>
                - 고객은 일본 로밍 요금을 확인하려고 함
                - 구체적인 요금제가 아직 결정되지 않음
                """, OutputMaskContext.empty()))
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
                """, OutputMaskContext.empty()))
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
                """, OutputMaskContext.empty())).isNull();
        assertThat(ChatSummaryNormalizer.normalize(
                "고객(QUESTION): 새로운 질문을 만들었습니다."
        , OutputMaskContext.empty())).isNull();
    }

    @Test
    void returnsNullForNullBlankOrFormattingOnlyOutput() {
        assertThat(ChatSummaryNormalizer.normalize(null, OutputMaskContext.empty())).isNull();
        assertThat(ChatSummaryNormalizer.normalize("   ", OutputMaskContext.empty())).isNull();
        assertThat(ChatSummaryNormalizer.normalize("<summary>```</summary>", OutputMaskContext.empty())).isNull();
        assertThat(ChatSummaryNormalizer.normalize("---", OutputMaskContext.empty())).isNull();
        assertThat(ChatSummaryNormalizer.normalize("***", OutputMaskContext.empty())).isNull();
        assertThat(ChatSummaryNormalizer.normalize("___", OutputMaskContext.empty())).isNull();
    }

    @Test
    void preservesPlainTextSummary() {
        assertThat(ChatSummaryNormalizer.normalize(
                "고객은 휴대폰 두 대의 가족 결합 할인을 확인하려고 함."
        , OutputMaskContext.empty())).isEqualTo("고객은 휴대폰 두 대의 가족 결합 할인을 확인하려고 함.");
    }

    @Test
    @DisplayName("문장 중간과 시작의 고정 민감정보 마스크를 보존한다")
    void 문장_중간과_시작의_마스크를_보존한다() {
        assertThat(ChatSummaryNormalizer.normalize("주민번호 *** 카드 *** 유심", masks("주민번호 *** 카드 *** 유심")))
                .isEqualTo("주민번호 *** 카드 *** 유심");
        assertThat(ChatSummaryNormalizer.normalize("*** 카드로 결제한 내역", masks("*** 카드로 결제한 내역")))
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
                """, masks("카드 ***로 `결제`한 내역", "주민번호 *** 확인", "카드 *** 안내")))
                .isEqualTo("납부 문의 카드 ***로 결제한 내역 주민번호 *** 확인 "
                        + "신청 경로는 홈페이지 카드 *** 안내");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "**카드 ***** | 카드 ***", "***** 카드** | *** 카드",
        "카드 ****** 문의 | 카드 ****** 문의"
    })
    @DisplayName("강조 기호가 마스크에 붙어도 고정 마스크를 유지하고 인접한 두 마스크는 합치지 않는다")
    void 강조와_붙은_마스크를_보존한다(String input, String expected) {
        assertThat(ChatSummaryNormalizer.normalize(input, masks(expected))).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "**요금제****변경** | 요금제****변경", "***강조*** | ***강조***", "카드 *** 안내 | 카드 *** 안내",
        "카드 **** 안내 | 카드 **** 안내", "**정상 요금 7700원·18개월** | 정상 요금 7700원·18개월"
    })
    @DisplayName("확실한 두 별표 강조는 제거하고 실제 마스크와 구분할 수 없는 별표 묶음은 보존한다")
    void 출처_불일치만으로_마스크일_수_있는_별표를_삭제하지_않는다(String input, String expected) {
        assertThat(ChatSummaryNormalizer.normalize(input, OutputMaskContext.empty())).isEqualTo(expected);
    }

    @Test
    @DisplayName("확인된 마스크와 확실한 두 별표 강조를 각각 처리한다")
    void 마스크와_강조_별표를_각각_처리한다() {
        assertThat(ChatSummaryNormalizer.normalize("카드 *** 안내와 **강조**", masks("카드 *** 안내와")))
                .isEqualTo("카드 *** 안내와 강조");
    }

    @Test
    @DisplayName("확인된 마스크에 붙은 나머지 별표도 다른 마스크일 수 있으면 개수를 줄이지 않는다")
    void 마스크에_붙은_모호한_별표_묶음을_보존한다() {
        var inspected = new ChatInputInspector(new ObjectMapper()).inspect("4111111111111111 카드 문의");
        var source = new OutputMaskContext(List.of(OutputMaskContext.Source.from(inspected)));

        assertThat(ChatSummaryNormalizer.normalize("******* 카드 문의", source)).isEqualTo("******* 카드 문의");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "카드번호 *** 납부 문의 | 카드번호 *** 납부 문의",
        "주민번호 *** 확인, 카드 ***로 납부 | 주민번호 *** 확인, 카드 ***로 납부",
        "**카드번호 *** 납부** | 카드번호 *** 납부",
        "*** 납부 문의 | *** 납부 문의", "카드 *** | 카드 ***",
        "카드 ****** 납부 | 카드 ****** 납부", "카드 ******* 납부 | 카드 ******* 납부",
        "카드 *** 납부와 ***강조*** | 카드 *** 납부와 ***강조***"
    })
    @DisplayName("요약에서 주변 문구가 달라져도 실제 마스크 표시와 개수를 유지한다")
    void 재작성한_요약의_마스크_표시를_보존한다(String input, String expected) {
        var inspector = new ChatInputInspector(new ObjectMapper());
        var inspected = inspector.inspect("제 카드번호는 4111111111111111인데요 납부 문의");
        var source = new OutputMaskContext(List.of(OutputMaskContext.Source.from(inspected)));

        assertThat(ChatSummaryNormalizer.normalize(input, source)).isEqualTo(expected);
        assertThat(ChatSummaryNormalizer.normalize(input, OutputMaskContext.empty())).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"씨***발", "개***새끼", "씨 *** 발", "개 *** 새끼", "씨******발"})
    @DisplayName("별표 표시를 보존해도 실제 별표 욕설을 신뢰한 마스크 인용으로 바꾸지 않는다")
    void 보존한_별표_욕설은_출력_검사에서_차단한다(String unsafe) throws Exception {
        var mapper = new ObjectMapper();
        var inspected = new ChatInputInspector(mapper).inspect("카드 4111111111111111 납부 문의");
        var sources = new OutputMaskContext(List.of(OutputMaskContext.Source.from(inspected)));
        String normalized = ChatSummaryNormalizer.normalize(unsafe, sources);
        var guard = new ChatOutputGuard(mapper);
        var answer = new ChatAnswer(ChatMessage.MessageType.ANSWER, normalized, null, List.of(), null);

        assertThat(normalized).isEqualTo(unsafe);
        assertThatThrownBy(() -> guard.verify(answer, sources)).isInstanceOf(ChatOutputBlockedException.class);
    }

    private OutputMaskContext masks(String... contents) {
        return new OutputMaskContext(Arrays.stream(contents).map(content -> {
            List<MaskSpan> spans = new ArrayList<>();
            for (int offset = content.indexOf(MaskSpan.MASK); offset >= 0;
                    offset = content.indexOf(MaskSpan.MASK, offset + MaskSpan.MASK.length())) {
                spans.add(new MaskSpan(offset, offset + MaskSpan.MASK.length()));
            }
            return new OutputMaskContext.Source(content, spans);
        }).toList());
    }
}
