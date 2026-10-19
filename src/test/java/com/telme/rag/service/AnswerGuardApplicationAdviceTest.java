package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnswerGuardApplicationAdviceTest {
    private final AnswerGuard guard = new AnswerGuard();
    private static final String VISIT = "유심 재발급 시 신분증을 가지고 매장을 방문하는 절차를 안내했습니다.";
    private static final String RECORD = "이번 상담에서는 재발급 신청을 접수하거나 완료하지 않았습니다.";

    @Test
    @DisplayName("근거 없는 신청 권유만 제거하고 신분증과 방문 절차 및 미처리 기록을 남긴다")
    void 추가_신청_권유를_제거한다() {
        String answer = VISIT + " 매장에서는 재발급 절차를 진행해 드리니 방문하시어 신청하시면 됩니다.";

        assertThat(guard.trimUnsupportedApplicationAdvice(answer, VISIT + " " + RECORD))
                .isEqualTo(VISIT + " " + RECORD);
    }

    @Test
    @DisplayName("같은 문장에 신청 권유가 섞여도 신분증과 방문 근거는 복원한다")
    void 혼합_문장의_방문_근거를_보존한다() {
        assertThat(guard.trimUnsupportedApplicationAdvice(
                "유심 재발급은 신분증을 가지고 매장을 방문하시어 신청하시면 됩니다.", VISIT + " " + RECORD))
                .isEqualTo(VISIT + " " + RECORD);
    }

    @Test
    @DisplayName("근거에 있는 신청 지침은 방문자 조건까지 그대로 남긴다")
    void 실제_신청_조건은_삭제하지_않는다() {
        String evidence = "대리인은 위임장과 신분증을 지참한 경우에만 매장에서 유심 재발급을 신청하시면 됩니다.";

        assertThat(guard.trimUnsupportedApplicationAdvice("유심 재발급을 신청하세요.", evidence))
                .isEqualTo(evidence);
    }

    @Test
    @DisplayName("로밍 신청 근거를 이심 신청 답변에 가져오지 않는다")
    void 다른_대상의_신청_근거를_가져오지_않는다() {
        String evidence = "로밍은 앱에서 신청하세요. eSIM 발급 비용은 2,750원입니다.";

        assertThat(guard.trimUnsupportedApplicationAdvice("eSIM을 신청하세요.", evidence))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("잘못된 신청 문장만 제거하고 이심 비용은 유지한다")
    void 다른_대상의_신청만_제거하고_비용을_남긴다() {
        String cost = "eSIM 발급 비용은 2,750원입니다.";

        assertThat(guard.applyEvidencePolicy(
                cost + " eSIM을 신청하세요.", "로밍은 앱에서 신청하세요. " + cost, "eSIM 비용을 알려주세요."))
                .isEqualTo(cost);
    }

    @ParameterizedTest
    @ValueSource(strings = {"매장에서 신청하시면 됩니다.", "신청하세요.", "그것을 신청하세요."})
    @DisplayName("대상이 생략된 신청 문장은 근거가 하나여도 복원하지 않는다")
    void 대상이_불명확하면_복원하지_않는다(String answer) {
        String evidence = "대리인은 위임장과 신분증을 지참한 경우에만 매장에서 유심 재발급을 신청하시면 됩니다.";

        assertThat(guard.trimUnsupportedApplicationAdvice(answer, evidence))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("같은 유심이라도 해지를 재발급 절차로 바꾸지 않는다")
    void 같은_대상의_다른_업무는_복원하지_않는다() {
        String evidence = "유심 재발급은 신분증을 지참하여 매장에서 신청하세요.";

        assertThat(guard.trimUnsupportedApplicationAdvice("유심 해지를 신청하세요.", evidence))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("이심과 유심의 같은 발급 업무도 서로 바꾸지 않는다")
    void 같은_업무의_다른_대상은_복원하지_않는다() {
        assertThat(guard.trimUnsupportedApplicationAdvice(
                "eSIM 발급을 신청하세요.", "유심 발급은 신분증을 지참하여 매장에서 신청하세요."))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("같은 요금제 변경이라도 상품이 다르면 원문을 복원하지 않는다")
    void 상품명이_다르면_복원하지_않는다() {
        assertThat(guard.trimUnsupportedApplicationAdvice(
                "5G 요금제 변경을 신청하세요.", "LTE 요금제 변경은 매장에서 신청하시면 됩니다."))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("같은 유심 재발급이라도 명의자가 다르면 원문을 복원하지 않는다")
    void 명의자가_다르면_복원하지_않는다() {
        assertThat(guard.trimUnsupportedApplicationAdvice(
                "어머니 유심 재발급을 신청하세요.", "아버지 유심 재발급은 매장에서 신청하시면 됩니다."))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("상품명과 업무가 같은 경우 신분증 조건을 포함한 원문을 복원한다")
    void 같은_상품의_업무_조건을_복원한다() {
        String evidence = "여행팩 A1 변경은 신분증을 지참하여 매장에서 신청하시면 됩니다.";

        assertThat(guard.trimUnsupportedApplicationAdvice("여행팩 A1 변경을 신청하세요.", evidence))
                .isEqualTo(evidence);
    }

    @Test
    @DisplayName("서로 다른 업무의 신청 근거가 함께 있어도 일치하는 단일 원문만 복원한다")
    void 여러_근거에서_일치하는_업무만_복원한다() {
        String evidence = "eSIM 발급은 본인 신분증을 지참하여 매장에서 신청하시면 됩니다.";

        assertThat(guard.trimUnsupportedApplicationAdvice(
                "eSIM 발급을 신청하세요.", "로밍은 앱에서 신청하세요. " + evidence))
                .isEqualTo(evidence);
    }

    @Test
    @DisplayName("같은 업무의 신청 근거가 여러 개면 방문자를 임의로 정하지 않는다")
    void 같은_업무의_근거가_여러_개면_복원하지_않는다() {
        String evidence = "유심 재발급은 본인 신분증을 지참하여 매장에서 신청하세요. "
                + "대리인은 위임장과 신분증을 지참하여 매장에서 유심 재발급을 신청하세요.";

        assertThat(guard.trimUnsupportedApplicationAdvice("유심 재발급을 신청하시면 됩니다.", evidence))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("본인 신청 안내에 대리인 전용 절차를 가져오지 않는다")
    void 명시한_방문자가_다르면_복원하지_않는다() {
        String evidence = "대리인은 위임장과 신분증을 지참하여 매장에서 유심 재발급을 신청하세요.";

        assertThat(guard.trimUnsupportedApplicationAdvice("본인은 유심 재발급을 신청하시면 됩니다.", evidence))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("신분증과 방문 창구가 같아도 다른 업무의 방문 근거를 가져오지 않는다")
    void 방문_복원으로_다른_업무를_우회하지_않는다() {
        String evidence = "로밍 변경 시 신분증을 가지고 매장을 방문해야 합니다.";
        String answer = "eSIM 발급은 신분증을 가지고 매장을 방문하여 신청하세요.";

        assertThat(guard.trimUnsupportedApplicationAdvice(answer, evidence))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(guard.applyEvidencePolicy(answer, evidence, "eSIM 발급 절차를 알려주세요."))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("잘못된 창구가 섞여도 다른 업무의 방문 절차로 복원하지 않는다")
    void 창구_복원으로_다른_업무를_우회하지_않는다() {
        String answer = "eSIM 발급은 신분증을 가지고 홈페이지 또는 매장을 방문하여 신청하세요.";
        String evidence = "로밍 변경 시 신분증을 가지고 매장을 방문해야 합니다.";

        assertThat(guard.applyEvidencePolicy(answer, evidence, "eSIM 발급 절차를 알려주세요."))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("같은 업무의 방문 근거를 복원할 때 대리인과 위임장 조건을 유지한다")
    void 방문_복원에서_대리인_준비물을_유지한다() {
        String evidence = "대리인은 유심 재발급 시 위임장과 신분증을 가지고 매장을 방문해야 합니다.";
        String answer = "유심 재발급은 신분증을 가지고 매장을 방문하여 신청하세요.";

        assertThat(guard.applyEvidencePolicy(answer, evidence, "유심 재발급 절차를 알려주세요."))
                .isEqualTo(evidence);
    }

    @Test
    @DisplayName("대상과 업무가 여러 개 섞인 근거를 단일 업무의 신청 절차로 사용하지 않는다")
    void 혼합_대상의_절차를_복원하지_않는다() {
        assertThat(guard.trimUnsupportedApplicationAdvice(
                "eSIM 발급을 신청하시면 됩니다.", "로밍 변경과 eSIM 발급은 앱에서 신청하세요."))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    @DisplayName("근거와 완전히 같은 신청 문장은 변경하지 않는다")
    void 원문과_동일한_문장은_그대로_남긴다() {
        String evidence = "대리인은 위임장과 신분증을 지참한 경우에만 매장에서 유심 재발급을 신청하세요.";

        assertThat(guard.trimUnsupportedApplicationAdvice(evidence, evidence)).isEqualTo(evidence);
    }

    @ParameterizedTest
    @ValueSource(strings = {"제가 신청해 드리겠습니다.", "제가 신청을 도와드리겠습니다.", "신청해 보세요."})
    @DisplayName("접수를 대신하겠다는 근거 없는 제안만 있으면 답변 불가로 처리한다")
    void 근거_없는_접수_제안은_차단한다(String answer) {
        assertThat(guard.trimUnsupportedApplicationAdvice(answer, "비용은 7,700원입니다."))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }
}
