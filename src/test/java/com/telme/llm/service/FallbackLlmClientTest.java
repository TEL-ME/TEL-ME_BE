package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    @DisplayName("스트리밍은 주 모델이 성공하면 대체 모델을 부르지 않는다")
    void 스트리밍_주_모델이_성공하면_그대로_쓴다() {
        LlmStreamHandler handler = mock(LlmStreamHandler.class);
        doAnswer(invocation -> {
            LlmStreamHandler target = invocation.getArgument(1);
            target.onToken("주 응답");
            target.onComplete();
            return null;
        }).when(primary).stream(any(), any());

        client.stream(request, handler);

        verify(handler).onToken("주 응답");
        verify(handler).onComplete();
        verify(handler, never()).onError(any());
        verifyNoInteractions(fallback);
    }

    @Test
    @DisplayName("스트리밍은 주 모델이 첫 글자 전에 실패를 알리면 대체 모델로 다시 부르고, 그 실패는 호출자에게 넘기지 않는다")
    void 스트리밍_첫_글자_전_실패는_대체한다() {
        LlmStreamHandler handler = mock(LlmStreamHandler.class);
        doAnswer(invocation -> {
            invocation.<LlmStreamHandler>getArgument(1).onError(new IllegalStateException("호출 실패"));
            return null;
        }).when(primary).stream(any(), any());

        client.stream(request, handler);

        verify(fallback).stream(request, handler);
        verify(handler, never()).onError(any());
    }

    @Test
    @DisplayName("스트리밍은 주 모델이 첫 글자 전에 예외를 던져도 대체 모델로 다시 부른다")
    void 스트리밍_첫_글자_전_예외도_대체한다() {
        LlmStreamHandler handler = mock(LlmStreamHandler.class);
        doThrow(new IllegalStateException("연결 실패")).when(primary).stream(any(), any());

        client.stream(request, handler);

        verify(fallback).stream(request, handler);
    }

    @Test
    @DisplayName("스트리밍은 글자가 나간 뒤의 실패를 그대로 넘기고 대체하지 않는다")
    void 스트리밍_글자가_나간_뒤_실패는_넘긴다() {
        LlmStreamHandler handler = mock(LlmStreamHandler.class);
        IllegalStateException failure = new IllegalStateException("중간 실패");
        doAnswer(invocation -> {
            LlmStreamHandler target = invocation.getArgument(1);
            target.onToken("앞부분");
            target.onError(failure);
            return null;
        }).when(primary).stream(any(), any());

        client.stream(request, handler);

        verify(handler).onToken("앞부분");
        verify(handler).onError(failure);
        verifyNoInteractions(fallback);
    }
}
