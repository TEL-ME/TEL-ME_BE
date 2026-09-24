package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.rag.exception.AnswerGuardException;
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
    @DisplayName("답변이 null이면 빈 문자열로 둔다")
    void 답변이_null이면_빈_문자열() {
        assertThat(guard.trimAfterNoEvidence(null)).isEmpty();
    }

    @Test
    @DisplayName("쉼표로 끝나는 서두가 붙어도 뒤를 잘라낸다")
    void 쉼표_서두() {
        String answer = "죄송합니다, " + AnswerPromptTemplates.NO_EVIDENCE_ANSWER
                + " 기상청 웹사이트를 참고해 주세요.";

        assertThat(guard.trimAfterNoEvidence(answer))
                .isEqualTo("죄송합니다, " + AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("마침표로 끝나는 서두가 붙어도 뒤를 잘라낸다")
    void 마침표_서두() {
        String answer = "죄송합니다. " + AnswerPromptTemplates.NO_EVIDENCE_ANSWER
                + " 기상청 웹사이트를 참고해 주세요.";

        assertThat(guard.trimAfterNoEvidence(answer))
                .isEqualTo("죄송합니다. " + AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("앞 문장은 남기고 문구 뒤만 잘라낸다")
    void 앞_문장은_남긴다() {
        String answer = "신규 가입은 24개월 약정입니다. 기기변경은 "
                + AnswerPromptTemplates.NO_EVIDENCE_ANSWER
                + " 번호이동은 30개월까지 가능합니다.";

        assertThat(guard.trimAfterNoEvidence(answer))
                .isEqualTo("신규 가입은 24개월 약정입니다. 기기변경은 "
                        + AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
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

        assertThatThrownBy(() -> guard.verifyAmounts(answer, context, "로밍 얼마인가요?"))
                .isInstanceOf(AnswerGuardException.class)
                .hasMessageContaining("근거에 없는 금액")
                .satisfies(e -> assertThat(((GeneralException) e).getErrorCode())
                        .isEqualTo(LlmErrorCode.INVALID_RESPONSE));
    }

    @Test
    @DisplayName("근거에 있는 금액만 쓰면 통과시킨다")
    void 근거에_있는_금액은_통과한다() {
        String context = "7일 기간권 39,000원이 가장 유리합니다. 일 단위 요금제는 9,900원입니다.";
        String answer = "7일 기간권 39,000원을 추천드립니다.";

        assertThatCode(() -> guard.verifyAmounts(answer, context, "로밍 얼마인가요?"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("고객이 질문에 쓴 금액을 되받는 것은 통과시킨다")
    void 질문에_있는_금액은_통과한다() {
        String context = "요금제 변경은 월 1회 가능합니다.";
        String answer = "말씀하신 50,000원 요금제는 월 1회 변경 가능합니다.";

        assertThatCode(() -> guard.verifyAmounts(answer, context, "50,000원 요금제도 바꿀 수 있나요?"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("만 단위 근거를 원 단위로 풀어 써도 통과시킨다")
    void 만_단위를_원_단위로_풀어도_통과한다() {
        String context = "기본 한도는 월 30만 원입니다. 본인 인증을 하면 월 100만 원까지 올릴 수 있습니다.";
        String answer = "기본 한도는 300,000원이고 본인 인증 시 1,000,000원까지 가능합니다.";

        assertThatCode(() -> guard.verifyAmounts(answer, context, "결제 한도 얼마예요?"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("만 단위 근거에 없는 금액은 막는다")
    void 만_단위_근거에_없는_금액은_막는다() {
        String context = "기본 한도는 월 30만 원입니다.";
        String answer = "기본 한도는 500,000원입니다.";

        assertThatThrownBy(() -> guard.verifyAmounts(answer, context, "결제 한도 얼마예요?"))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("원으로 시작하는 낱말은 금액으로 보지 않는다")
    void 원으로_시작하는_낱말은_금액이_아니다() {
        String context = "요금제 변경은 한 달에 1회만 가능합니다.";

        assertThatCode(() -> guard.verifyAmounts("남은 할부 1 원금과 3 원인을 확인하세요.", context, ""))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("금액이 아닌 숫자는 검사하지 않는다")
    void 금액이_아닌_숫자는_통과한다() {
        String context = "번호이동 처리는 매일 09:00부터 20:00까지 진행됩니다.";
        String answer = "매일 오전 9시부터 오후 8시까지 신청하실 수 있습니다.";

        assertThatCode(() -> guard.verifyAmounts(answer, context, "번호이동 언제 되나요?"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("쉼표 표기가 달라도 같은 금액으로 본다")
    void 쉼표_표기가_달라도_통과한다() {
        assertThatCode(() -> guard.verifyAmounts("39000원입니다.", "39,000원입니다.", ""))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("long 범위를 넘는 금액도 파싱 오류 없이 검사한다")
    void 아주_큰_금액도_검사한다() {
        assertThatThrownBy(() -> guard.verifyAmounts("12345678901234567890원입니다.", "9,900원입니다.", ""))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("근거에 없는 안내 창구가 들어간 문장만 걷어낸다")
    void 근거에_없는_창구_문장_제거() {
        String answer = "유심 재발급 비용은 7,700원입니다. 자세한 내용은 샵 이벤트 페이지를 확인해 주세요.";
        String context = "유심 재발급 비용이 얼마예요? 7,700원입니다.";

        assertThat(guard.trimUngroundedChannels(answer, context, "유심 얼마예요?"))
                .isEqualTo("유심 재발급 비용은 7,700원입니다.");
    }

    @Test
    @DisplayName("근거에 있는 창구는 그대로 둔다")
    void 근거에_있는_창구는_유지() {
        String answer = "매장을 방문하시면 즉시 발급됩니다.";
        String context = "유심은 어디서 받아요? 매장을 방문하시면 즉시 발급됩니다.";

        assertThat(guard.trimUngroundedChannels(answer, context, "어디서 받아요?"))
                .isEqualTo(answer);
    }

    @Test
    @DisplayName("고객이 질문에 쓴 창구는 지어낸 것이 아니다")
    void 질문에_있는_창구는_유지() {
        String answer = "고객센터로 문의하시면 확인됩니다.";
        String context = "문의는 어떻게 하나요? 담당 부서에서 확인해 드립니다.";

        assertThat(guard.trimUngroundedChannels(answer, context, "고객센터로 물어봐야 하나요?"))
                .isEqualTo(answer);
    }

    @Test
    @DisplayName("남는 문장이 없으면 근거 없음으로 돌린다")
    void 전부_걷히면_근거_없음() {
        String answer = "샵 이벤트 페이지에서 확인해 주세요. 홈페이지 메뉴에서도 가능합니다.";

        assertThat(guard.trimUngroundedChannels(answer, "유심 비용은 7,700원입니다.", "어디서 봐요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("답변이 null이나 공백이면 그대로 둔다")
    void 창구_검사_빈_입력() {
        assertThat(guard.trimUngroundedChannels(null, "근거", "질문")).isEmpty();
        assertThat(guard.trimUngroundedChannels("  ", "근거", "질문")).isEqualTo("  ");
    }

    @Test
    @DisplayName("근거 숫자로 만든 배수를 차단한다")
    void 근거에_없는_배수_차단() {
        String answer = "기본 30만 원에서 100만 원까지 올라가니 약 3.3배 늘어납니다.";
        String context = "본인 인증을 하면 기본 월 30만 원에서 최대 월 100만 원까지 상향됩니다.";

        assertThatThrownBy(() -> guard.verifyMeasures(answer, context, "몇 배 늘어나요?"))
                .isInstanceOf(AnswerGuardException.class)
                .hasMessageContaining("3.3배");
    }

    @Test
    @DisplayName("근거에 있는 수치는 통과시킨다")
    void 근거에_있는_수치는_통과() {
        String answer = "개통 후 14일 이내이고 1회에 한해 가능합니다.";
        String context = "개통 후 14일 이내에 미개봉이면 1회에 한해 교환할 수 있습니다.";

        assertThatCode(() -> guard.verifyMeasures(answer, context, "교환 되나요?"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("근거에 없는 비교 표현이 든 문장을 걷어낸다")
    void 근거에_없는_비교_문장_제거() {
        String answer = "우편은 월 500원이고 이메일은 무료입니다. 이메일이 더 저렴하고 편리합니다.";
        String context = "우편 청구서는 월 500원, 이메일 청구서는 무료입니다.";

        assertThat(guard.trimUngroundedComparisons(answer, context, "뭐가 나아요?"))
                .isEqualTo("우편은 월 500원이고 이메일은 무료입니다.");
    }

    @Test
    @DisplayName("근거에 있는 비교 표현은 그대로 둔다")
    void 근거에_있는_비교는_유지() {
        String answer = "매장이 온라인보다 빠릅니다.";
        String context = "매장에서 발급받으면 온라인 택배 신청보다 빠릅니다.";

        assertThat(guard.trimUngroundedComparisons(answer, context, "뭐가 빨라요?"))
                .isEqualTo(answer);
    }
}
