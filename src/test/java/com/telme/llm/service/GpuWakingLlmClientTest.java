package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.llm.dto.req.LlmRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class GpuWakingLlmClientTest {

    private final LlmClient delegate = mock(LlmClient.class);
    private final GpuInstanceStarter starter = mock(GpuInstanceStarter.class);
    private final GpuWakingLlmClient client = new GpuWakingLlmClient(delegate, starter);
    private final LlmRequest request = LlmRequest.builder().systemPrompt("s").userPrompt("u").build();

    @Test
    @DisplayName("대체 호출(generate)이면 GPU 기동을 먼저 요청하고 원래 클라이언트의 답을 그대로 돌려준다")
    void 대체_generate는_기동_요청_뒤_그대로_부른다() {
        when(delegate.generate(any())).thenReturn("엑사원 응답");

        assertThat(LlmFallbackScope.run(() -> client.generate(request))).isEqualTo("엑사원 응답");

        InOrder order = inOrder(starter, delegate);
        order.verify(starter).requestStart();
        order.verify(delegate).generate(request);
    }

    @Test
    @DisplayName("대체 호출(stream)이면 GPU 기동을 먼저 요청하고 같은 핸들러로 넘긴다")
    void 대체_stream도_기동_요청_뒤_그대로_부른다() {
        LlmStreamHandler handler = mock(LlmStreamHandler.class);

        LlmFallbackScope.run(() -> client.stream(request, handler));

        InOrder order = inOrder(starter, delegate);
        order.verify(starter).requestStart();
        order.verify(delegate).stream(request, handler);
    }

    @Test
    @DisplayName("대체가 아닌 직접 호출(조건 추출 파싱 재시도 등)은 GPU 기동을 요청하지 않는다")
    void 직접_호출은_기동하지_않는다() {
        when(delegate.generate(any())).thenReturn("엑사원 응답");

        client.generate(request);
        client.stream(request, mock(LlmStreamHandler.class));

        verify(starter, never()).requestStart();
        verify(delegate).generate(request);
    }

    @Test
    @DisplayName("원래 클라이언트의 실패(꺼진 GPU 연결 실패 등)는 그대로 올려 보내 재시도·대체 흐름을 바꾸지 않는다")
    void 원래_클라이언트_실패는_그대로_던진다() {
        IllegalStateException failure = new IllegalStateException("연결 실패");
        when(delegate.generate(any())).thenThrow(failure);

        assertThatThrownBy(() -> LlmFallbackScope.run(() -> client.generate(request))).isSameAs(failure);
        verify(starter).requestStart();
    }
}
