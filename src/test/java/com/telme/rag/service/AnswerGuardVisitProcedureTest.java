package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.rag.exception.AnswerGuardException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AnswerGuardVisitProcedureTest {

    private static final String QUESTION = "유심을 분실했는데 비용과 무료 조건, 절차를 알려주세요.";
    private static final String PROCEDURE = "신분증을 가지고 매장을 방문하는 절차를 안내했습니다.";
    private static final String CONTEXT = "평가용 유심 재발급 비용은 7,700원입니다. "
            + "분실 재발급에는 비용이 부과됩니다. 기기 불량으로 확인된 경우에만 무료입니다. "
            + PROCEDURE + " 이번 상담에서는 재발급 신청을 접수하거나 완료하지 않았습니다.";
    private static final String MIXED_PROCEDURE = "절차로는 신분증을 지참하여 가까운 LG샵 이벤트 이벤트 페이지 "
            + "또는 지정된 매장을 방문하시면 됩니다.";

    private final AnswerGuard guard = new AnswerGuard();

    @ParameterizedTest
    @CsvSource({
            "eSIM 발급, 로밍 변경",
            "유심 해지, 유심 재발급",
            "어머니 유심 재발급, 아버지 유심 재발급",
            "5G 요금제 변경, LTE 요금제 변경",
            "유심 재발급, 로밍 변경과 유심 재발급"
    })
    @DisplayName("신청 권유가 없는 방문 안내도 대상과 업무가 다르면 복원하지 않는다")
    void 일반_방문의_다른_대상과_업무를_복원하지_않는다(String business, String otherBusiness) {
        String answer = business + " 시 신분증을 가지고 홈페이지 또는 매장을 방문하세요.";
        String evidence = otherBusiness + " 시 신분증을 가지고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy(answer, evidence, business + " 절차를 알려주세요."))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("생략된 방문 대상에 명시된 다른 업무를 임의로 연결하지 않는다")
    void 생략된_대상에_다른_업무를_연결하지_않는다() {
        String answer = "신분증을 가지고 홈페이지 또는 매장을 방문하세요.";
        String evidence = "로밍 변경 시 신분증을 가지고 매장을 방문해야 합니다.";

        assertThat(guard.trimUngroundedChannels(answer, evidence, QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("같은 업무의 일반 방문 안내에서도 대리인과 위임장 조건을 복원한다")
    void 일반_방문의_같은_업무와_방문자_조건을_복원한다() {
        String answer = "유심 재발급 시 신분증을 가지고 홈페이지 또는 매장을 방문하세요.";
        String evidence = "대리인은 유심 재발급 시 위임장과 신분증을 가지고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy(answer, evidence, QUESTION)).isEqualTo(evidence);
    }

    @Test
    @DisplayName("본인 방문 안내에 대리인 전용 방문 절차를 가져오지 않는다")
    void 일반_방문의_서로_다른_방문자_조건을_복원하지_않는다() {
        String answer = "본인은 유심 재발급 시 신분증을 가지고 홈페이지 또는 매장을 방문하세요.";
        String evidence = "대리인은 유심 재발급 시 위임장과 신분증을 가지고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy(answer, evidence, QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("실제 유심 생성 원문에서 잘못된 창구를 제거하고 방문 근거를 보존한다")
    void 실제_유심_원문의_방문_절차를_보존한다() {
        String first = "유심을 분실하셨을 때 재발급 비용은 7,700원이 부과됩니다. "
                + "무료로 재발급 받으실 수 있는 조건은 기기 자체의 불량이 확인된 경우에 한정됩니다.";
        String last = "상담 과정에서는 실제 재발급 신청을 진행하지 않았습니다.";
        String answer = first + " " + MIXED_PROCEDURE + " " + last;

        assertThat(guard.applyEvidencePolicy(answer, CONTEXT, QUESTION))
                .isEqualTo(first + " " + PROCEDURE + " " + last)
                .doesNotContain("LG샵", "이벤트", "페이지");
    }

    @Test
    @DisplayName("방문 근거의 대리인 조건과 준비물을 문장 그대로 남긴다")
    void 방문_근거의_조건을_그대로_남긴다() {
        String context = "대리인 방문은 위임장과 신분증을 지참한 경우에만 매장에서 가능합니다.";
        String answer = "신분증만 가지고 홈페이지 또는 매장을 방문하면 됩니다.";

        assertThat(guard.trimUngroundedChannels(answer, context, QUESTION)).isEqualTo(context);
    }

    @Test
    @DisplayName("방문 불가 근거를 방문 가능으로 바꾸지 않는다")
    void 방문_불가_근거를_그대로_남긴다() {
        String context = "신분증 없이 매장을 방문하면 재발급할 수 없습니다.";
        String answer = "신분증 없이 앱 또는 매장을 방문하시면 됩니다.";

        assertThat(guard.trimUngroundedChannels(answer, context, QUESTION)).isEqualTo(context);
    }

    @Test
    @DisplayName("대리점 방문도 동일한 단일 근거가 있으면 보존한다")
    void 대리점_방문의_단일_근거를_남긴다() {
        String context = "신분증을 가지고 대리점을 방문해야 합니다.";
        String answer = "신분증을 가지고 이벤트 페이지 또는 대리점을 방문하세요.";

        assertThat(guard.trimUngroundedChannels(answer, context, QUESTION)).isEqualTo(context);
    }

    @Test
    @DisplayName("다른 방문 창구의 근거를 현재 절차에 가져오지 않는다")
    void 다른_방문_창구의_근거를_가져오지_않는다() {
        String answer = "신분증을 가지고 홈페이지 또는 대리점을 방문하세요.";

        assertThat(guard.trimUngroundedChannels(answer, PROCEDURE, QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("방문 근거가 둘이면 임의로 골라 복구하지 않는다")
    void 방문_근거가_둘이면_복구하지_않는다() {
        String context = PROCEDURE + " 대리인은 신분증과 위임장을 가지고 매장을 방문해야 합니다.";

        assertThat(guard.trimUngroundedChannels(MIXED_PROCEDURE, context, QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("준비물이나 방문 근거가 없으면 기존 삭제를 유지한다")
    void 준비물이나_방문_근거가_없으면_삭제한다() {
        assertThat(guard.trimUngroundedChannels(MIXED_PROCEDURE, "매장을 방문해야 합니다.", QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(guard.trimUngroundedChannels(MIXED_PROCEDURE, "신분증이 필요합니다.", QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("모델이 방문 절차를 말하지 않았으면 근거에서 새로 추가하지 않는다")
    void 모델에_없는_방문_절차를_추가하지_않는다() {
        assertThat(guard.trimUngroundedChannels("이벤트 페이지에서 확인하세요.", CONTEXT, QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("잘못된 창구가 없는 정상 문구는 그대로 유지한다")
    void 정상_문구는_그대로_유지한다() {
        String answer = "신분증을 지참하여 매장을 방문하세요.";

        assertThat(guard.trimUngroundedChannels(answer, CONTEXT, QUESTION)).isEqualTo(answer);
    }

    @Test
    @DisplayName("빈 근거와 빈 답변은 절차 복구를 하지 않는다")
    void 빈_근거와_빈_답변을_처리한다() {
        assertThat(guard.trimUngroundedChannels(null, CONTEXT, QUESTION)).isEmpty();
        assertThat(guard.trimUngroundedChannels("  ", CONTEXT, QUESTION)).isEqualTo("  ");
        assertThat(guard.trimUngroundedChannels(MIXED_PROCEDURE, null, QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(guard.trimUngroundedChannels(MIXED_PROCEDURE, "  ", QUESTION))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("방문 복구 이후에도 다른 문장의 잘못된 금액을 차단한다")
    void 방문_복구_뒤에도_잘못된_금액을_차단한다() {
        String answer = "재발급 비용은 8,800원입니다. " + MIXED_PROCEDURE;

        assertThatThrownBy(() -> guard.applyEvidencePolicy(answer, CONTEXT, QUESTION))
                .isInstanceOf(AnswerGuardException.class);
    }
}
