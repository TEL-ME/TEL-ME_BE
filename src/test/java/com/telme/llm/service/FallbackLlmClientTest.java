package com.telme.llm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @Test
    @DisplayName("스트리밍은 글자가 나간 뒤 완료 처리에서 예외가 나면(가드 차단 등) 대체하지 않고 오류로 넘긴다")
    void 스트리밍_글자가_나간_뒤_완료_처리_예외는_넘긴다() {
        LlmStreamHandler handler = mock(LlmStreamHandler.class);
        IllegalStateException rejection = new IllegalStateException("가드 차단");
        doThrow(rejection).when(handler).onComplete();
        doAnswer(invocation -> {
            LlmStreamHandler target = invocation.getArgument(1);
            target.onToken("답");
            target.onComplete();
            return null;
        }).when(primary).stream(any(), any());

        client.stream(request, handler);

        verify(handler).onError(rejection);
        verifyNoInteractions(fallback);
    }

    @Test
    @DisplayName("대체 모델은 대체 표시(LlmFallbackScope) 안에서 불리고, 주 모델 호출과 끝난 뒤에는 표시가 없다")
    void 대체_호출만_표시된다() {
        boolean[] seen = new boolean[2];
        when(primary.generate(any())).thenAnswer(invocation -> {
            seen[0] = LlmFallbackScope.active();
            throw new IllegalStateException("호출 실패");
        });
        when(fallback.generate(any())).thenAnswer(invocation -> {
            seen[1] = LlmFallbackScope.active();
            return "대체 응답";
        });

        client.generate(request);

        assertThat(seen[0]).as("주 모델 호출 중").isFalse();
        assertThat(seen[1]).as("대체 모델 호출 중").isTrue();
        assertThat(LlmFallbackScope.active()).as("호출이 끝난 뒤").isFalse();
    }

    @Test
    @DisplayName("스트리밍도 첫 글자 전 실패로 대체할 때만 표시되고, 대체가 실패해도 표시가 남지 않는다")
    void 스트리밍_대체도_표시되고_지워진다() {
        boolean[] seen = new boolean[1];
        doAnswer(invocation -> {
            invocation.<LlmStreamHandler>getArgument(1).onError(new IllegalStateException("호출 실패"));
            return null;
        }).when(primary).stream(any(), any());
        doAnswer(invocation -> {
            seen[0] = LlmFallbackScope.active();
            throw new IllegalStateException("대체도 실패");
        }).when(fallback).stream(any(), any());

        try {
            client.stream(request, mock(LlmStreamHandler.class));
        } catch (IllegalStateException expected) {
            // 대체 실패는 그대로 올라온다
        }

        assertThat(seen[0]).isTrue();
        assertThat(LlmFallbackScope.active()).isFalse();
    }

    @Test
    @DisplayName("대체를 막는 게이트면 generate는 대체 모델을 부르지 않고 게이트의 예외를 던진다")
    void 게이트가_막으면_generate는_대체하지_않는다() {
        IllegalStateException blocked = new IllegalStateException("재시도 안내");
        FallbackLlmClient gated = new FallbackLlmClient(primary, fallback, (r, f) -> blocked);
        when(primary.generate(any())).thenThrow(new IllegalStateException("호출 실패"));

        assertThatThrownBy(() -> gated.generate(request)).isSameAs(blocked);
        verifyNoInteractions(fallback);
    }

    @Test
    @DisplayName("대체를 막는 게이트면 스트리밍은 첫 글자 전 실패에 대체하지 않고 게이트의 예외를 onError로 넘긴다")
    void 게이트가_막으면_stream은_대체하지_않는다() {
        IllegalStateException blocked = new IllegalStateException("재시도 안내");
        FallbackLlmClient gated = new FallbackLlmClient(primary, fallback, (r, f) -> blocked);
        LlmStreamHandler handler = mock(LlmStreamHandler.class);
        doAnswer(invocation -> {
            invocation.<LlmStreamHandler>getArgument(1).onError(new IllegalStateException("호출 실패"));
            return null;
        }).when(primary).stream(any(), any());

        gated.stream(request, handler);

        verify(handler).onError(blocked);
        verifyNoInteractions(fallback);
    }
}
