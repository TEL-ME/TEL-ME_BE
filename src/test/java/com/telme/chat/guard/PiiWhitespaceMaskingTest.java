package com.telme.chat.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class PiiWhitespaceMaskingTest {
    private final ChatInputInspector inspector = new ChatInputInspector(new ObjectMapper());

    static Stream<String> 번호_구분자() {
        return Stream.of("  ", "\t", "\u00a0", "\u2007", "\u202f", "\u2009", "\u3000", " \t\u00a0 ", " \t-\u00a0 ");
    }

    static Stream<Arguments> 카드번호_뒤_금액() {
        return Stream.of("4111111111111111", "5555555555554444", "378282246310005",
                        "6011111111111117", "3530111333300000")
                .flatMap(card -> Stream.of(" ", "\t", "\u00a0")
                        .flatMap(separator -> Stream.of("3,000", "18,000", "4,900", "55,000", "1,200,000")
                                .map(amount -> Arguments.of(card, separator, amount))));
    }

    @ParameterizedTest
    @MethodSource("카드번호_뒤_금액")
    @DisplayName("유효 카드번호 뒤 쉼표 포함 금액은 카드번호 일부로 합치지 않고 약정과 함께 보존한다")
    void 카드번호_뒤_금액의_앞자리를_보존한다(String digits, String separator, String amount) {
        String card = String.join(separator, digits.split("(?<=\\G.{4})"));
        String input = "카드번호 " + card + separator + amount + "원 납부 문의, 약정 18개월";

        var result = inspector.inspect(input);

        assertThat(result.content()).isEqualTo("카드번호 ***" + separator + amount + "원 납부 문의, 약정 18개월");
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.ambiguousCondition()).isFalse();
        assertThat(result.hasQuestion()).isTrue();
        assertThat(result.hasProfanity()).isFalse();
    }

    @Test
    @DisplayName("쉼표 금액 경계 보완은 과거 v3 요청 해시의 금액 소실 형태를 바꾸지 않는다")
    void 과거_v3_금액_경계의_해시를_유지한다() {
        String input = "카드번호 4111 1111 1111 1111 3,000원 납부 문의";

        assertThat(inspector.previousV3FingerprintContent(input))
                .isEqualTo("카드번호 [카드번호],000원 납부 문의");
        assertThat(inspector.inspect(input).fingerprintContent())
                .isEqualTo("카드번호 [카드번호] 3,000원 납부 문의");
    }

    @Test
    @DisplayName("긴 가로 공백 뒤 금액이 없는 문의에서도 공백과 질문은 그대로 보존한다")
    void 긴_공백을_금액_경계로_오인하지_않는다() {
        String spaces = " ".repeat(1800);

        assertThat(inspector.inspect("카드번호 4111111111111111" + spaces + "납부 문의").content())
                .isEqualTo("카드번호 ***" + spaces + "납부 문의");
    }

    @ParameterizedTest
    @MethodSource("번호_구분자")
    @DisplayName("붙여넣기 공백과 탭으로 나눈 카드번호는 가리고 주변 금액과 약정 기간을 보존한다")
    void 카드번호만_가리고_문의와_조건을_보존한다(String separator) {
        String card = String.join(separator, "4111", "1111", "1111", "1111");
        String input = "🙂 카드번호 " + card + "\t약정 18개월, 요금 7,700원 납부 문의";

        var result = inspector.inspect(input);

        assertThat(result.content()).isEqualTo("🙂 카드번호 ***\t약정 18개월, 요금 7,700원 납부 문의");
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.hasQuestion()).isTrue();
        assertThat(result.ambiguousCondition()).isFalse();
        assertThat(result.hasProfanity()).isFalse();
        int start = input.indexOf(card);
        assertThat(result.maskSpans()).isEqualTo(List.of(new MaskSpan(start, start + 3)));
        assertThat(result.fingerprintContent()).doesNotContain("4111", "1111");
    }

    @ParameterizedTest
    @MethodSource("번호_구분자")
    @DisplayName("주민번호의 앞뒤 숫자 묶음 사이 공백만 확장하고 실제 문의와 숫자 조건을 보존한다")
    void 주민번호를_가리고_금액과_기간을_보존한다(String separator) {
        String resident = "990101" + separator + "1234567";
        String input = "🙂 주민번호 " + resident + ", 약정 18개월이고 금액 5만원 문의";

        var result = inspector.inspect(input);

        assertThat(result.content()).isEqualTo("🙂 주민번호 ***, 약정 18개월이고 금액 5만원 문의");
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.hasQuestion()).isTrue();
        assertThat(result.ambiguousCondition()).isFalse();
        int start = input.indexOf(resident);
        assertThat(result.maskSpans()).isEqualTo(List.of(new MaskSpan(start, start + 3)));
    }

    @ParameterizedTest
    @MethodSource("번호_구분자")
    @DisplayName("특수 공백이 있어도 번호 소개만 남은 입력은 실제 문의로 판단하지 않는다")
    void 번호만_남으면_재입력_대상이다(String separator) {
        for (String input : List.of(
                "주민번호 990101" + separator + "1234567",
                "카드번호 " + String.join(separator, "4111", "1111", "1111", "1111"))) {
            var result = inspector.inspect(input);

            assertThat(result.wasMasked()).isTrue();
            assertThat(result.hasQuestion()).isFalse();
            assertThat(result.ambiguousCondition()).isFalse();
            assertThat(result.hasProfanity()).isFalse();
        }
    }

    @ParameterizedTest
    @MethodSource("번호_구분자")
    @DisplayName("구분자 확대 후에도 번호와 기간이 함께 유효 번호로 읽히면 전체 보호와 재입력 판정을 유지한다")
    void 모호한_기간은_추측해_남기지_않는다(String separator) {
        String card = String.join(separator, "4111", "1111", "1111", "1111");

        var result = inspector.inspect("카드 " + card + separator + "18개월로 약정 문의");

        assertThat(result.content()).isEqualTo("카드 ***개월로 약정 문의");
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.ambiguousCondition()).isTrue();
        assertThat(result.content()).doesNotContain("18", "4111");
    }

    @ParameterizedTest
    @MethodSource("번호_구분자")
    @DisplayName("명시한 주민번호 뒤의 명확한 기간은 번호 구분자와 무관하게 보존한다")
    void 명확한_주민번호_뒤_기간을_보존한다(String separator) {
        var result = inspector.inspect("주민번호 990101" + separator + "1234567 4개월 약정 문의");

        assertThat(result.content()).isEqualTo("주민번호 *** 4개월 약정 문의");
        assertThat(result.ambiguousCondition()).isFalse();
        assertThat(result.hasQuestion()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\t", "\u00a0", "  "})
    @DisplayName("이전 정책에서 감지하지 못했던 표기는 기존 영수증 비교용 해시 입력을 바꾸지 않는다")
    void 이전_요청_해시의_탐색_규칙을_유지한다(String separator) {
        String input = "카드 " + String.join(separator, "4111", "1111", "1111", "1111") + " 납부 문의";

        assertThat(inspector.previousFingerprintContent(input)).isEqualTo(input);
        assertThat(inspector.inspect(input).fingerprintContent()).isEqualTo("카드 [카드번호] 납부 문의");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "요금 7\u00a0700원, 약정 18개월 문의", "금액\t7700원, 기간\t18개월 문의",
        "2026\u202f10\u202f16 사용 내역과 24개월 약정 문의", "요금 5만원과 18개월 약정 문의",
        "주민번호 변경 후 명의변경은 어떻게 하나요?", "주민번호 없이 유심 재발급 가능한가요?",
        "카드번호 4111\t1111\t1111\t1112 납부 문의", "주민번호 991332\t1234567 문의"
    })
    @DisplayName("일반 금액·날짜·기간과 번호 검증을 통과하지 않은 입력 및 정상 문의는 그대로 둔다")
    void 정상_문의와_숫자_조건을_잘못_가리지_않는다(String input) {
        var result = inspector.inspect(input);

        assertThat(result.content()).isEqualTo(input);
        assertThat(result.wasMasked()).isFalse();
        assertThat(result.hasQuestion()).isTrue();
    }

    @Test
    @DisplayName("앞의 숫자 후보가 실패해도 뒤의 특수 공백 카드번호를 전체 보호한다")
    void 앞_후보의_실패가_뒤_카드번호를_누락하지_않는다() {
        var result = inspector.inspect("고객번호 1234567890123\t4111\u00a01111\u00a01111\u00a01111 납부 문의");

        assertThat(result.content()).isEqualTo("고객번호 1234567890123\t*** 납부 문의");
        assertThat(result.wasMasked()).isTrue();
    }

    @Test
    @DisplayName("줄바꿈을 사이에 둔 별도 숫자 항목은 하나의 번호로 연결하지 않는다")
    void 줄바꿈을_숫자_항목의_경계로_유지한다() {
        String input = "4111\n1111\n1111\n1111 사용 내역";

        assertThat(inspector.inspect(input).content()).isEqualTo(input);
        assertThat(inspector.inspect(input).wasMasked()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n", "\u2028", "\u2029", "\u200b", "--", "."})
    @DisplayName("줄바꿈과 지원하지 않는 구분자를 넘어서 별도 숫자를 임의로 합치지 않는다")
    void 지원하지_않는_경계를_넘어_숫자를_합치지_않는다(String separator) {
        String card = "4111" + separator + "1111" + separator + "1111" + separator + "1111";
        String resident = "990101" + separator + "1234567";

        assertThat(inspector.inspect(card + " 문의").content()).isEqualTo(card + " 문의");
        assertThat(inspector.inspect(resident + " 문의").content()).isEqualTo(resident + " 문의");
    }

    @ParameterizedTest
    @MethodSource("번호_구분자")
    @DisplayName("새 공백 형식과 기존 번호가 함께 있어도 단일 구분자였던 v3 해시 입력을 재현한다")
    void v3_비교에만_기존_공백_경계를_사용한다(String separator) {
        String spaced = String.join(separator, "4111", "1111", "1111", "1111");
        String input = "카드 4111111111111111, 카드번호 " + spaced + " 납부 문의";
        String legacy = separator.equals("\u3000") ? "[카드번호]" : spaced;

        assertThat(inspector.previousV3FingerprintContent(input))
                .isEqualTo("카드 [카드번호], 카드번호 " + legacy + " 납부 문의");
        assertThat(inspector.inspect(input).fingerprintContent())
                .isEqualTo("카드 [카드번호], 카드번호 [카드번호] 납부 문의");
    }

    @Test
    @DisplayName("마스크가 둘이면 앞 마스크로 줄어든 저장 문장의 실제 위치를 기록한다")
    void 복수_번호와_이모지의_마스크_위치를_보존한다() {
        String input = "🙂 주민번호 990101\t1234567, 카드번호 4111\u00a01111\u00a01111\u00a01111 문의";
        String expected = "🙂 주민번호 ***, 카드번호 *** 문의";

        var result = inspector.inspect(input);

        assertThat(result.content()).isEqualTo(expected);
        assertThat(result.maskSpans()).containsExactly(
                new MaskSpan(expected.indexOf("***"), expected.indexOf("***") + 3),
                new MaskSpan(expected.lastIndexOf("***"), expected.lastIndexOf("***") + 3));
    }
}
