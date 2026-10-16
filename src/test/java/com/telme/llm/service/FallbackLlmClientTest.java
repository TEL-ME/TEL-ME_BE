package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.llm.dto.req.LlmRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FallbackLlmClientTest {

    private final LlmClient primary = mock(LlmClient.class);
    private final LlmClient fallback = mock(LlmClient.class);
    private final FallbackLlmClient client = new FallbackLlmClient(primary, fallback);
    private final LlmRequest request = LlmRequest.builder().systemPrompt("s").userPrompt("u").build();

    @Test
    @DisplayName("주 모델이 성공하면 대체 모델을 부르지 않는다")
    void 주_모델이_성공하면_그대로_쓴다() {
        when(primary.generate(any())).thenReturn("주 응답");

        assertThat(client.generate(request)).isEqualTo("주 응답");
        verifyNoInteractions(fallback);
    }

    @Test
    @DisplayName("주 모델 호출이 실패하면 같은 요청을 대체 모델로 보낸다")
    void 주_모델이_실패하면_대체한다() {
        when(primary.generate(any())).thenThrow(new IllegalStateException("호출 실패"));
        when(fallback.generate(any())).thenReturn("대체 응답");

        assertThat(client.generate(request)).isEqualTo("대체 응답");
        verify(fallback).generate(request);
    }

    @Test
    @DisplayName("스트리밍은 대체 모델이 처리한다")
    void 스트리밍은_대체_모델이_처리한다() {
        LlmStreamHandler handler = mock(LlmStreamHandler.class);

        client.stream(request, handler);

        verify(fallback).stream(request, handler);
        verifyNoInteractions(primary);
    }
}
