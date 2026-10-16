package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.rag.exception.AnswerGuardException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

// 금액을 '무료'로 바꾼 답변, 같은 시각의 다른 표기, 시각 조건의 뜻을 뒤집은 답변
class AnswerGuardClaimTest {

    private final AnswerGuard guard = new AnswerGuard();

    private String apply(String answer, String context, String query) {
        return guard.applyEvidencePolicy(answer, context, query);
    }

    @Test
    @DisplayName("근거에 금액이 있는데 무료라고 한 문장은 제거한다")
    void 금액을_무료로_바꾼_문장을_제거한다() {
        String context = "유심 재발급 비용은 7,700원입니다.";

        assertThat(apply("유심 재발급은 무료입니다.", context, "유심 재발급 비용이 얼마예요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(apply("유심 재발급 신청은 가능합니다. 비용은 무료입니다.", "유심 재발급 신청은 가능합니다. 유심 재발급 비용은 7,700원입니다.",
                "유심 재발급 비용이 얼마예요?"))
                .isEqualTo("유심 재발급 신청은 가능합니다.");
        assertThat(apply("유심 재발급은 비용이 없습니다.", context, "유심 재발급 비용이 얼마예요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("근거에 무료가 있거나 무료가 아니라고 말한 문장은 유지한다")
    void 근거에_무료가_있으면_유지한다() {
        assertThat(apply("이메일 청구서는 무료입니다.", "우편 청구서는 월 500원, 이메일 청구서는 무료입니다.", "이메일 청구서 비용은요?"))
                .isEqualTo("이메일 청구서는 무료입니다.");
        assertThat(apply("무료는 아니며 7,700원입니다.", "유심 재발급 비용은 7,700원입니다.", "유심 재발급은 무료인가요?"))
                .isEqualTo("무료는 아니며 7,700원입니다.");
        assertThat(apply("무료로 제공되지 않고 7,700원이 청구됩니다.", "유심 재발급 비용은 7,700원입니다.", "유심 재발급은 무료인가요?"))
                .isEqualTo("무료로 제공되지 않고 7,700원이 청구됩니다.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"오후 7시 30분", "저녁 7시 30분", "19시 30분", "7시 30분", "19:30"})
    @DisplayName("같은 시각을 다르게 적어도 근거로 인정한다")
    void 같은_시각의_다른_표기를_통과시킨다(String time) {
        String answer = "접수는 " + time + "까지 가능합니다.";

        assertThatCode(() -> apply(answer, "접수는 19:30까지 가능합니다.", "접수는 몇 시까지예요?")).doesNotThrowAnyException();
        assertThat(apply(answer, "접수는 19:30까지 가능합니다.", "접수는 몇 시까지예요?")).isEqualTo(answer);
    }

    @Test
    @DisplayName("근거를 한글 시각으로 적어도 콜론 표기 답변을 통과시킨다")
    void 근거가_한글_시각이어도_통과시킨다() {
        assertThatCode(() -> apply("접수는 19:30까지 가능합니다.", "접수는 오후 7시 30분까지 가능합니다.", "접수는 몇 시까지예요?"))
                .doesNotThrowAnyException();
        assertThatCode(() -> apply("운영 시간은 오전 9시부터 오후 6시까지입니다.", "운영 시간은 09:00부터 18:00까지입니다.", "운영 시간은요?"))
                .doesNotThrowAnyException();
        assertThatCode(() -> apply("상담은 7시 반까지 가능합니다.", "상담은 19:30까지 가능합니다.", "상담은 몇 시까지예요?"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"오전 7시 30분", "오후 8시", "오후 7시", "19시 40분", "7시 40분", "20:30"})
    @DisplayName("한글로 적어도 근거와 다른 시각은 차단한다")
    void 한글_표기여도_다른_시각은_차단한다(String time) {
        assertThatThrownBy(() -> apply("접수는 " + time + "까지 가능합니다.", "접수는 19:30까지 가능합니다.", "접수는 몇 시까지예요?"))
                .isInstanceOf(AnswerGuardException.class);
    }

    @Test
    @DisplayName("시각에 붙은 분을 별도 시간 수치로 세지 않는다")
    void 시각의_분은_수치로_세지_않는다() {
        assertThatCode(() -> guard.verifyMeasures("접수는 오후 7시 30분까지입니다.", "접수는 19:30까지입니다.", "접수 시간은요?"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.verifyMeasures("접수 후 45분 안에 처리됩니다.", "접수 후 30분 안에 처리됩니다.", "처리 시간은요?"))
                .isInstanceOf(AnswerGuardException.class)
                .hasMessageContaining("45분");
    }

    @Test
    @DisplayName("시각 조건의 가능 여부를 근거와 반대로 말한 문장은 제거한다")
    void 시각_조건의_뜻을_뒤집은_문장을_제거한다() {
        String context = "19:30 이후에는 접수가 불가합니다.";

        assertThat(apply("19:30 이후에도 접수가 가능합니다.", context, "19:30 이후에도 접수돼요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(apply("오후 7시 30분 이후에도 접수할 수 있습니다.", context, "저녁에도 접수돼요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(apply("접수는 가능합니다. 19:30 이후에도 가능합니다.", context, "접수 시간이 언제예요?"))
                .isEqualTo("접수는 가능합니다.");
    }

    @Test
    @DisplayName("같은 뜻을 다르게 말한 시각 조건은 유지한다")
    void 같은_뜻의_시각_조건은_유지한다() {
        assertThat(apply("19:30 이후에는 접수할 수 없습니다.", "19:30 이후에는 접수가 불가합니다.", "저녁에도 접수돼요?"))
                .isEqualTo("19:30 이후에는 접수할 수 없습니다.");
        assertThat(apply("19:30 이후에는 접수가 불가합니다.", "접수는 19:30까지 가능합니다.", "저녁에도 접수돼요?"))
                .isEqualTo("19:30 이후에는 접수가 불가합니다.");
        assertThat(apply("19:30까지 접수할 수 있습니다.", "19:30 이후에는 접수가 불가합니다.", "몇 시까지 접수돼요?"))
                .isEqualTo("19:30까지 접수할 수 있습니다.");
    }

    @Test
    @DisplayName("근거 문장에 가능과 불가가 함께 있으면 뒤집었다고 보지 않는다")
    void 근거가_가능과_불가를_함께_말하면_제거하지_않는다() {
        String context = "19:30까지는 접수가 가능하고 19:30 이후에는 접수가 불가합니다.";

        assertThat(apply("19:30까지 접수가 가능합니다.", context, "몇 시까지 접수돼요?"))
                .isEqualTo("19:30까지 접수가 가능합니다.");
    }
}
