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

    @Test
    @DisplayName("고객이 말한 시각을 근거의 시각과 견주는 문장은 통과시키고, 고객 시각을 사실로 단정하면 차단한다")
    void 고객이_말한_시각은_근거_시각과_견줄_때만_통과시킨다() {
        String context = "개통 접수는 19:30까지 가능합니다. 19:30 이후에는 접수가 불가합니다.";
        String query = "저녁 8시에 개통 접수 되나요?";

        assertThatCode(() -> apply("저녁 8시는 접수 마감 시간인 19:30 이후이므로 접수가 불가능합니다.", context, query))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> apply("저녁 8시에도 접수할 수 있습니다.", context, query))
                .isInstanceOf(AnswerGuardException.class);
    }

    @Test
    @DisplayName("고객이 고르라는 뜻의 '편하신 방법'은 비교 표현으로 보지 않는다")
    void 고객이_고르는_편한_방법은_통과시킨다() {
        String context = "매장에 가시거나 고객센터에 전화하시거나 홈페이지로 신청하시면 됩니다.";

        assertThat(apply("고객센터 전화, 홈페이지, 또는 매장 방문 중 편하신 방법으로 신청하시면 됩니다.", context, "인터넷 해지 신청 방법"))
                .contains("편하신 방법");
        assertThat(apply("홈페이지로 신청하는 것이 더 편리합니다.", context, "인터넷 해지 신청 방법"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("근거의 상한을 넘는 기간을 '범위에 포함되지 않는다'고 한 답변은 통과시킨다")
    void 범위에_포함되지_않는다는_표현을_범위_밖으로_본다() {
        String context = "요금납부확인서는 최근 3년분까지만 발급됩니다.";
        String answer = "요금납부확인서는 최근 3년분까지만 발급되며, 4년 전 기록은 발급 범위에 포함되지 않습니다.";

        assertThat(apply(answer, context, "4년 전 요금 납부 기록을 발급받을 수 있나요?")).isEqualTo(answer);
        assertThatThrownBy(() -> apply("4년 전 기록도 발급됩니다.", context, "4년 전 요금 납부 기록을 발급받을 수 있나요?"))
                .isInstanceOf(AnswerGuardException.class);
    }

    @Test
    @DisplayName("근거의 '하루'와 답변의 '1일'은 같은 기간으로 본다")
    void 하루와_1일을_같은_기간으로_본다() {
        String context = "일주일씩 쓰시면 7일 기간권 39,000원이 있습니다. 하루짜리는 9,900원입니다.";

        assertThat(apply("일일권은 1일짜리이며, 7일 기간권도 제공됩니다.", context, "해외로밍패스는 며칠짜리까지 있어요?"))
                .contains("1일짜리");
        assertThatThrownBy(() -> apply("3일짜리도 있습니다.", context, "해외로밍패스는 며칠짜리까지 있어요?"))
                .isInstanceOf(AnswerGuardException.class);
    }

    @Test
    @DisplayName("근거에 없는 '하루'를 답변이 쓰면 새 수치로 세지 않는다")
    void 답변의_하루는_수치로_세지_않는다() {
        String context = "일 단위로는 9,900원입니다.";

        assertThat(apply("일 단위로는 9,900원이고 하루만 써도 됩니다.", context, "일본 로밍 요금")).contains("하루만");
    }

    @Test
    @DisplayName("'차이점'은 장점을 말한 비교 표현으로 보지 않는다")
    void 차이점은_장점이_아니다() {
        String context = "eSIM은 2,750원에 새로 발급받고 지원 단말이어야 합니다. 듀얼심으로 최대 2개 회선을 함께 쓸 수 있는 점이 다릅니다.";
        String answer = "eSIM은 듀얼심 기능이 있다는 점이 차이점입니다.";

        assertThat(apply(answer, context, "유심과 eSIM은 뭐가 달라요?")).isEqualTo(answer);
        assertThat(apply("eSIM은 듀얼심이라는 이점이 있습니다.", context, "유심과 eSIM은 뭐가 달라요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("요금이 없다는 조건·주의 문장은 무료 단정으로 보지 않는다")
    void 조건_문장의_없다는_무료_단정이_아니다() {
        String fee = "번호 변경 수수료는 2,200원입니다.";

        assertThat(apply("밀린 요금이 없다면 번호 변경이 가능합니다.", fee, "번호 변경 수수료가 얼마예요?"))
                .isEqualTo("밀린 요금이 없다면 번호 변경이 가능합니다.");
        assertThat(apply("미납 요금이 없으면 신청할 수 있습니다.", fee, "번호 변경 수수료가 얼마예요?"))
                .isEqualTo("미납 요금이 없으면 신청할 수 있습니다.");
        assertThat(apply("추가 요금이 없도록 데이터 사용량을 확인하세요.", "로밍 하루 9,900원입니다.", "로밍 요금 알려줘"))
                .isEqualTo("추가 요금이 없도록 데이터 사용량을 확인하세요.");
        assertThat(apply("번호 변경 수수료는 없습니다.", fee, "번호 변경 수수료가 얼마예요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(apply("번호 변경 수수료가 없어요.", fee, "번호 변경 수수료가 얼마예요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"미납 요금이 없을 때 번호 변경을 신청할 수 있습니다.", "미납 요금이 없는지 먼저 확인해 주세요.",
            "추가 비용이 들지 않도록 요금제를 확인하세요.", "추가 요금이 부과되지 않도록 확인하세요."})
    @DisplayName("조건·확인 표현의 '없을 때·없는지·들지 않도록'은 무료 단정으로 보지 않는다")
    void 조건_확인_표현은_통과시킨다(String answer) {
        assertThat(apply(answer, "번호 변경 수수료는 2,200원입니다.", "번호 변경 수수료가 얼마예요?")).isEqualTo(answer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"번호 변경 시 추가 비용이 들지 않습니다.", "번호 변경은 무료입니다.", "번호 변경 수수료는 부과되지 않습니다."})
    @DisplayName("근거의 금액과 반대로 무료라고 단정한 문장은 계속 제거한다")
    void 무료_단정은_계속_제거한다(String answer) {
        assertThat(apply(answer, "번호 변경 수수료는 2,200원입니다.", "번호 변경 수수료가 얼마예요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"부가서비스는 3 시설에서 이용 가능합니다.", "2024시즌 요금제는 아래와 같습니다.",
            "5 시니어 요금제가 있습니다.", "2 시행일 이후 적용됩니다."})
    @DisplayName("시각이 아닌 '숫자 + 시…' 낱말은 시각으로 읽지 않는다")
    void 시각이_아닌_시_낱말은_통과시킨다(String answer) {
        assertThat(apply(answer, "부가서비스는 매장과 온라인에서 가입할 수 있습니다.", "요금제 알려줘")).isEqualTo(answer);
    }

    @Test
    @DisplayName("24시 고객센터는 시각으로 읽지 않고 한글 시각은 그대로 비교한다")
    void 이십사시_고객센터는_통과시키고_시각은_유지한다() {
        assertThat(apply("분실 신고는 24시 고객센터에서 할 수 있습니다.", "고객센터는 24시간 운영합니다.", "분실 신고 어디서 해요?"))
                .isEqualTo("분실 신고는 24시 고객센터에서 할 수 있습니다.");
        assertThat(apply("7시가 마감입니다.", "마감은 19:00입니다.", "마감이 몇 시예요?")).isEqualTo("7시가 마감입니다.");
        assertThat(apply("7시 반까지 운영합니다.", "운영 시간은 19:30까지입니다.", "몇 시까지예요?")).isEqualTo("7시 반까지 운영합니다.");
        assertThatThrownBy(() -> apply("8시까지 운영합니다.", "운영 시간은 19:30까지입니다.", "몇 시까지예요?"))
                .isInstanceOf(AnswerGuardException.class);
    }
}
