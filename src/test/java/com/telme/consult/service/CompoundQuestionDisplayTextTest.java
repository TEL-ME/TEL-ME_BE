package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompoundQuestionDisplayTextTest {

    @Test
    void restoresSpacesFromOriginalQuestionWithoutChangingRoutingQuery() {
        String original = "번호이동 서류는 뭐고 요금제 변경은 어떻게 해?";

        assertThat(CompoundQuestionDisplayText.fromOriginal(original, "번호이동서류는뭐고"))
                .isEqualTo("번호이동 서류는 뭐고");
        assertThat(CompoundQuestionDisplayText.fromOriginal(original, "요금제변경은어떻게해"))
                .isEqualTo("요금제 변경은 어떻게 해");
    }

    @Test
    void keepsRoutingTextWhenItCannotBeMatchedToOriginalQuestion() {
        assertThat(CompoundQuestionDisplayText.fromOriginal("번호이동 서류 알려줘", "유심재발급비용"))
                .isEqualTo("유심재발급비용");
    }
}
