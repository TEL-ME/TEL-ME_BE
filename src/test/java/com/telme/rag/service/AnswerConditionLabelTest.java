package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.rag.dto.req.AnswerRequest;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnswerConditionLabelTest {

    private static String conditionsOf(
            Map<String, String> conditions, Map<String, String> labels) {
        return AnswerPromptTemplates.buildUserPrompt(
                AnswerRequest.builder()
                        .userQuery("미성년자도 가입 되나요")
                        .conditions(conditions)
                        .conditionLabels(labels)
                        .build(),
                "근거");
    }

    @Test
    @DisplayName("되물은 질문 문구를 조건 이름 대신 쓴다")
    void 되물은_질문을_쓴다() {
        String prompt = conditionsOf(
                Map.of("age", "예"), Map.of("age", "고객의 나이가 만 14세 이상인가요?"));

        assertThat(prompt).contains("- 고객의 나이가 만 14세 이상인가요?: 예");
        assertThat(prompt).doesNotContain("- age: 예");
    }

    @Test
    @DisplayName("되물은 질문이 없으면 기존 이름을 쓴다")
    void 되물은_질문이_없으면_기존_이름을_쓴다() {
        assertThat(conditionsOf(Map.of("location", "강남역"), Map.of()))
                .contains("- 지역: 강남역");
    }

    @Test
    @DisplayName("이름을 모르는 조건은 조건 이름을 그대로 쓴다")
    void 모르는_조건은_이름을_그대로_쓴다() {
        assertThat(conditionsOf(Map.of("unpaid_bill", "아니요"), Map.of()))
                .contains("- unpaid_bill: 아니요");
    }

    @Test
    @DisplayName("조건이 없으면 없음으로 보낸다")
    void 조건이_없으면_없음이다() {
        assertThat(conditionsOf(Map.of(), Map.of())).contains("[고객 조건]\n없음");
    }
}
