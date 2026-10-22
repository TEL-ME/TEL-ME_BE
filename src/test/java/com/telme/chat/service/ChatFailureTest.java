package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatFailureTest {

    @Test
    @DisplayName("재시도 가능 실패는 다시 시도 안내 문구와 retryable=true로 나간다")
    void 재시도_가능_실패() throws Exception {
        ChatFailure failure = new ChatFailure(ChatMessage.Status.FAILED, "LLM502-4");

        assertThat(failure.retryable()).isTrue();
        assertThat(failure.message()).isEqualTo("답변 생성에 실패했습니다. 다시 시도해 주세요.");
        assertThat(new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(failure))
                .get("retryable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("그 밖의 실패와 취소는 retryable=false다")
    void 그_밖의_실패는_재시도_불가() {
        assertThat(new ChatFailure(ChatMessage.Status.FAILED, "LLM502-0").retryable()).isFalse();
        assertThat(new ChatFailure(ChatMessage.Status.TIMEOUT, "LLM504-0").retryable()).isFalse();
        assertThat(new ChatFailure(ChatMessage.Status.CANCELLED, "USER_CANCELLED").retryable()).isFalse();
    }
}
