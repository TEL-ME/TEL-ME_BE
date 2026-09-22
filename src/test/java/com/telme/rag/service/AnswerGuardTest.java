package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnswerGuardTest {

    private final AnswerGuard guard = new AnswerGuard();

    @Test
    @DisplayName("답변 불가 문구 뒤에 덧붙은 설명을 잘라낸다")
    void 답변_불가_뒤를_잘라낸다() {
        String answer = AnswerPromptTemplates.NO_EVIDENCE_ANSWER
                + " 날씨 정보는 기상청 웹사이트를 참고해 주세요.";

        assertThat(guard.trimAfterNoEvidence(answer))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("답변 불가 문구가 없으면 그대로 둔다")
    void 일반_답변은_그대로_둔다() {
        String answer = "요금제는 한 달에 1회만 변경 가능합니다.";

        assertThat(guard.trimAfterNoEvidence(answer)).isEqualTo(answer);
    }

    @Test
    @DisplayName("근거에 없는 금액이 있으면 실패시킨다")
    void 근거에_없는_금액을_막는다() {
        String context = "데이터 무제한은 하루 12,100원입니다.";
        String answer = "일주일이면 총 84,700원이 됩니다.";

        assertThatThrownBy(() -> guard.verifyAmounts(answer, context))
                .isInstanceOf(GeneralException.class)
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode())
                        .isEqualTo(LlmErrorCode.INVALID_RESPONSE));
    }

    @Test
    @DisplayName("근거에 있는 금액만 쓰면 통과시킨다")
    void 근거에_있는_금액은_통과한다() {
        String context = "7일 기간권 39,000원이 가장 유리합니다. 일 단위 요금제는 9,900원입니다.";
        String answer = "7일 기간권 39,000원을 추천드립니다.";

        assertThatCode(() -> guard.verifyAmounts(answer, context)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("금액이 아닌 숫자는 검사하지 않는다")
    void 금액이_아닌_숫자는_통과한다() {
        String context = "번호이동 처리는 매일 09:00부터 20:00까지 진행됩니다.";
        String answer = "매일 오전 9시부터 오후 8시까지 신청하실 수 있습니다.";

        assertThatCode(() -> guard.verifyAmounts(answer, context)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("쉼표 표기가 달라도 같은 금액으로 본다")
    void 쉼표_표기가_달라도_통과한다() {
        assertThatCode(() -> guard.verifyAmounts("39000원입니다.", "39,000원입니다."))
                .doesNotThrowAnyException();
    }
}
