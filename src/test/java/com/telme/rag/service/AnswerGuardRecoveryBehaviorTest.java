package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnswerGuardRecoveryBehaviorTest {

    private static final String QUOTE = "일주일이면 7일 기간권 39,000원이 가장 유리합니다.";
    private static final String EVIDENCE = QUOTE
            + " 일 단위 요금제는 9,900원, 데이터 무제한은 하루 12,100원이라 같은 기간을 쓰시면 기간권보다 비쌉니다.";
    private static final String ANSWER = "일주일(7일) 로밍을 이용하신다면 7일 기간권이 39,000원으로 가장 저렴합니다.";
    private final AnswerGuard guard = new AnswerGuard();

    @Test
    void capturedComparisonHasExpectedOutcome() {
        assertThat(guard.applyEvidencePolicy(ANSWER, EVIDENCE, "일주일 로밍 뭐가 제일 싸요?"))
                .isEqualTo(QUOTE);
    }

    @Test
    void queryWithDifferentPeriodCannotTriggerRepair() {
        assertThat(guard.applyEvidencePolicy(ANSWER, EVIDENCE, "30일 로밍 뭐가 제일 싸요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    void unsupportedSpeedIsStillRemoved() {
        assertThat(guard.applyEvidencePolicy("매장 개통이 더 빠릅니다.", "매장에서 개통할 수 있습니다.", "어디서 해요?"))
                .isEqualTo(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    void missingPriceRelationHasExpectedOutcome() {
        assertThat(guard.applyEvidencePolicy(ANSWER, QUOTE, "일주일 로밍 뭐가 제일 싸요?"))
                .isEqualTo(QUOTE);
    }
}
