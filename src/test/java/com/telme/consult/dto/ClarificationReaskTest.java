package com.telme.consult.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClarificationReaskTest {

    @Test
    @DisplayName("한 줄 질문은 그대로 돌려준다")
    void 한_줄_질문은_그대로() {
        assertThat(ClarificationReask.questionOf("  현재 미납 요금이 있으신가요? ")).isEqualTo("현재 미납 요금이 있으신가요?");
    }

    @Test
    @DisplayName("복합 질문의 되묻기 메시지는 마지막 줄의 질문만 돌려준다")
    void 복합_질문은_마지막_줄만() {
        String content = "1. 유심 재발급 비용\n유심 재발급 비용은 7,700원입니다.\n\n2. 번호이동 필요 서류\n현재 미납 요금이 있으신가요?";

        assertThat(ClarificationReask.questionOf(content)).isEqualTo("현재 미납 요금이 있으신가요?");
    }
}
