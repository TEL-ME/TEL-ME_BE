package com.telme.chat.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class PiiMaskingBoundaryTest {
    private final ChatInputInspector inspector = new ChatInputInspector(new ObjectMapper());

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "카드번호 411111 1111111 111 | 카드번호 ***",
        "카드번호 411111-1111111-111 | 카드번호 ***",
        "고객번호 1234567890123 4111 1111 1111 1111 납부 문의 | 고객번호 1234567890123 *** 납부 문의",
        "4111111111111111 4 111111111111111 납부 문의 | *** *** 납부 문의",
        "🙂 카드번호 ４１１１１１ １１１１１１１ １１１ 납부 문의 | 🙂 카드번호 *** 납부 문의"
    })
    @DisplayName("겹친 주민번호 후보와 앞 숫자 후보가 있어도 실제 카드번호의 전체 범위를 보호한다")
    void 카드번호_겹침과_후보_누락을_보완한다(String input, String expected) {
        var result = inspector.inspect(input);

        assertThat(result.content()).isEqualTo(expected);
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.hasProfanity()).isFalse();
        assertThat(result.fingerprintContent()).doesNotContain("4111", "１１１１");
        assertThat(result.maskSpans()).allSatisfy(span ->
                assertThat(result.content().substring(span.start(), span.end())).isEqualTo("***"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "제 카드번호는 4111111111111111인데요", "제 카드번호는 4111111111111111이고요",
        "제 카드번호는 4111111111111111이요", "주민번호는 990101-1234567인데요",
        "주민등록번호는 990101-1234567이고요"
    })
    @DisplayName("번호를 소개하는 어미만 남으면 실제 문의가 없는 입력으로 판단한다")
    void 번호_소개_어미만_남으면_재입력이_필요하다(String input) {
        var result = inspector.inspect(input);

        assertThat(result.wasMasked()).isTrue();
        assertThat(result.hasQuestion()).isFalse();
        assertThat(result.hasProfanity()).isFalse();
        assertThat(result.ambiguousCondition()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "제 카드번호는 4111111111111111인데요 납부 방법은요?",
        "주민번호는 990101-1234567이고요 유심 재발급 비용이 얼마예요?",
        "주민번호 990101-1234567 4개월 약정 문의",
        "주민번호 990101-3234567 100000원 납부 문의",
        "카드 4111111111111111, 18개월 약정과 요금 7700원 문의",
        "카드 4111111111111111 약정 18개월 요금 5만원 납부 문의"
    })
    @DisplayName("명시한 주민번호 조건과 분리된 금액·기간 및 소개 어미 뒤의 실제 문의는 유지한다")
    void 명확한_상담_조건과_실제_문의를_유지한다(String input) {
        var result = inspector.inspect(input);
        String expected = input.replace("4111111111111111", "***")
                .replace("990101-1234567", "***").replace("990101-3234567", "***");

        assertThat(result.content()).isEqualTo(expected);
        assertThat(result.hasQuestion()).isTrue();
        assertThat(result.ambiguousCondition()).isFalse();
        assertThat(result.hasProfanity()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "카드 4111111111111111 18개월", "카드 4111111111111111 18개월 약정 문의",
        "카드 4111111111111111 003원 납부 문의"
    })
    @DisplayName("유효한 전체 카드와 분리된 조건으로 모두 읽히는 경우 전체를 가리고 모호성을 기록한다")
    void 모호한_조건을_보호한_뒤_재입력으로_분리한다(String input) {
        var result = inspector.inspect(input);

        assertThat(result.ambiguousCondition()).isTrue();
        assertThat(result.content()).doesNotContain("18", "003", "4111");
        assertThat(result.wasMasked()).isTrue();
        assertThat(result.hasProfanity()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "개월로", "개월동안", "개월짜리", "개월로는", "개월동안만", "개월짜리는",
        "개월짜리로", "개월짜리로는요",
        "개월간", "개월째", "개월치", "개월씩", "개월이요",
        "개월간은", "개월째는", "개월치로", "개월씩만", "개월이요?"
    })
    @DisplayName("모호한 기간의 누락 어미와 조사 결합도 마스킹·해시를 유지하며 재입력으로 분리한다")
    void 모호한_기간의_누락_어미를_재입력으로_분리한다(String ending) {
        var result = inspector.inspect("카드 4111111111111111 18" + ending + " 약정 문의");

        assertThat(result.content()).isEqualTo("카드 ***" + ending + " 약정 문의");
        assertThat(result.fingerprintContent()).isEqualTo("카드 [카드번호]" + ending + " 약정 문의");
        assertThat(result.maskSpans()).isEqualTo(List.of(new MaskSpan(3, 6)));
        assertThat(result.ambiguousCondition()).isTrue();
        assertThat(result.hasQuestion()).isTrue();
        assertThat(result.hasProfanity()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"개월로", "개월동안", "개월짜리", "개월간", "개월째", "개월치", "개월씩", "개월이요"})
    @DisplayName("주민번호와 겹친 기존 마스크·해시는 유지하고 소실된 조건은 재입력으로 안내한다")
    void 주민번호_겹침의_기존_마스킹과_해시를_변경하지_않는다(String ending) {
        var result = inspector.inspect("주민번호 990101-1234567 4" + ending + " 약정 문의");

        assertThat(result.content()).isEqualTo("주민번호 ***" + ending + " 약정 문의");
        assertThat(result.fingerprintContent()).isEqualTo("주민번호 [카드번호]" + ending + " 약정 문의");
        assertThat(result.maskSpans()).isEqualTo(List.of(new MaskSpan(5, 8)));
        assertThat(result.ambiguousCondition()).isTrue();
        assertThat(result.hasProfanity()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "003 | 원짜리", "3 | 원씩", "3 | 만원씩", "3 | 년째",
        "18 | 일간", "18 | 시간째", "18 | 분씩"
    })
    @DisplayName("지원하는 금액·기간 단위의 추가 어미도 기존 전체 번호 보호와 해시 문구를 유지한다")
    void 모호한_금액과_기간의_추가_어미를_재입력으로_분리한다(String number, String ending) {
        var result = inspector.inspect("카드 4111111111111111 " + number + ending + " 납부 문의");

        assertThat(result.content()).isEqualTo("카드 ***" + ending + " 납부 문의");
        assertThat(result.fingerprintContent()).isEqualTo("카드 [카드번호]" + ending + " 납부 문의");
        assertThat(result.maskSpans()).isEqualTo(List.of(new MaskSpan(3, 6)));
        assertThat(result.ambiguousCondition()).isTrue();
        assertThat(result.hasProfanity()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "카드 4111111111111111 12개월로 약정 문의",
        "카드 4111111111111111 12개월동안 납부 문의",
        "카드 4111111111111111 12개월짜리는 얼마예요?",
        "카드 4111111111111111 7700원으로 납부 문의",
        "카드 4111111111111111, 18개월로 약정 문의",
        "카드 4111111111111111 약정 18개월동안 납부 문의",
        "카드 4111111111111111 금액 003원짜리 납부 문의",
        "카드 4111111111111111 / 18개월짜리 약정 문의",
        "주민번호 990101-1234567 4개월 약정 문의",
        "주민번호 990101-1234567 18개월에 걸쳐 납부 문의",
        "주민번호 990101-3234567 100000원으로 납부 문의",
        "요금 7700원과 18개월로 약정 문의",
        "18개월짜리 요금제와 5만원 납부 문의",
        "제 카드번호는 4111111111111111인데요 재발급 비용이 얼마예요?",
        "카드 4111111111111111 12개월간 약정 문의",
        "카드 4111111111111111 12개월째 납부 문의",
        "카드 4111111111111111 12개월치 금액 문의",
        "카드 4111111111111111 12개월씩 납부 문의",
        "카드 4111111111111111 12개월이요",
        "카드 4111111111111111 7700원씩 납부 문의",
        "카드 4111111111111111, 18개월간 약정 문의",
        "카드 4111111111111111 약정 18개월째 문의",
        "카드 4111111111111111 / 18개월치 납부 문의",
        "카드 4111111111111111 기간 18개월씩 납부 문의",
        "카드 4111111111111111, 18개월이요",
        "카드 4111111111111111 금액 3만원씩 납부 문의",
        "18개월간 약정하고 3만원씩 납부할 수 있나요?",
        "3년째 사용 중인데 18일간 로밍과 18시간째 장애, 18분씩 통화 문의"
    })
    @DisplayName("어미 판정을 추가해도 정상 문의와 분리된 금액·기간 및 해시 문구는 유지한다")
    void 정상_문의와_분리된_조건의_마스킹과_해시를_유지한다(String input) {
        var result = inspector.inspect(input);

        assertThat(result.content()).isEqualTo(input.replace("4111111111111111", "***")
                .replace("990101-1234567", "***").replace("990101-3234567", "***"));
        assertThat(result.fingerprintContent()).isEqualTo(input.replace("4111111111111111", "[카드번호]")
                .replace("990101-1234567", "[주민등록번호]")
                .replace("990101-3234567", "[주민등록번호]"));
        assertThat(result.ambiguousCondition()).isFalse();
        assertThat(result.hasQuestion()).isTrue();
        assertThat(result.hasProfanity()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "카드번호 4111111111111111 18분실 신고 | 카드번호 ***분실 신고 | 카드번호 [카드번호]분실 신고",
        "카드번호 4111111111111111 18일반 문의 | 카드번호 ***일반 문의 | 카드번호 [카드번호]일반 문의",
        "카드번호 4111111111111111 003원룸 주소 변경 | 카드번호 ***원룸 주소 변경 | 카드번호 [카드번호]원룸 주소 변경"
    })
    @DisplayName("분실·일반·원룸의 일부를 조건 단위로 잘라 정상 문의를 재입력으로 보내지 않는다")
    void 정상_단어를_조건_단위로_오인하지_않는다(String input, String content, String fingerprint) {
        var result = inspector.inspect(input);

        assertThat(result.content()).isEqualTo(content);
        assertThat(result.fingerprintContent()).isEqualTo(fingerprint);
        assertThat(result.ambiguousCondition()).isFalse();
        assertThat(result.hasQuestion()).isTrue();
        assertThat(result.hasProfanity()).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "990101-1234567 | 4개월", "990101-1234567 | 18개월", "990101-1234567 | 24개월",
        "990101-1234567 | 100000원", "990101-1234567 | 003원", "990101-1234567 | 5만원",
        "990101-3234567 | 4개월", "990101-3234567 | 18개월", "990101-3234567 | 24개월",
        "990101-3234567 | 100000원", "990101-3234567 | 10000000원", "990101-3234567 | 5만원"
    })
    @DisplayName("주민번호로 명시한 입력의 금액·기간은 카드 검증 결과와 관계없이 보존한다")
    void 주민번호_뒤의_정상_조건을_카드_범위로_합치지_않는다(String resident, String condition) {
        var result = inspector.inspect("주민번호 " + resident + " " + condition + " 유심 비용 문의");

        assertThat(result.content()).isEqualTo("주민번호 *** " + condition + " 유심 비용 문의");
        assertThat(result.fingerprintContent()).isEqualTo("주민번호 [주민등록번호] " + condition + " 유심 비용 문의");
        assertThat(result.ambiguousCondition()).isFalse();
        assertThat(result.hasQuestion()).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "카드번호 411111 1111111 111 납부 문의 | 카드번호 [주민등록번호] 111 납부 문의",
        "제 카드번호는 4111111111111111인데요 | 제 카드번호는 [카드번호]인데요",
        "카드 4111111111111111 18개월 약정 문의 | 카드 [카드번호]개월 약정 문의",
        "고객번호 1234567890123 4111 1111 1111 1111 납부 문의 | 고객번호 1234567890123 4111 1111 1111 1111 납부 문의"
    })
    @DisplayName("배포된 이전 정책의 비교 문구만 당시 경계로 재현하고 새 검사 결과에는 섞지 않는다")
    void 이전_영수증의_비교_경계를_재현한다(String input, String expected) {
        assertThat(inspector.previousFingerprintContent(input)).isEqualTo(expected);
        assertThat(inspector.inspect(input).fingerprintContent()).doesNotContain("4111");
    }
}
