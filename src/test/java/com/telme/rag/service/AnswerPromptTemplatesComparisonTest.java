package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnswerPromptTemplatesComparisonTest {
    @Test
    void comparisonPromptDiscouragesUnaskedRecommendation() {
        assertThat(AnswerPromptTemplates.systemPromptFor("A와 B의 종류를 비교해줘"))
                .contains("선택 권고");
    }

    @Test
    void leavesOtherAnswersUntouched() {
        assertThat(AnswerPromptTemplates.systemPromptFor("A는 몇 종류야?"))
                .isEqualTo(AnswerPromptTemplates.ANSWER_SYSTEM_PROMPT);
    }
}
