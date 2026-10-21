package com.telme.rag.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class RagAnswerLlmClientSelectionTest {

    private final LlmClient exaone = mock(LlmClient.class);
    private final LlmClient bedrock = mock(LlmClient.class);

    @Test
    @DisplayName("플래그가 꺼져 있으면 엑사원으로 답한다")
    void 꺼져_있으면_엑사원이다() {
        assertThat(RagAnswerLlmConfiguration.answerClient(new RagAnswerProperties(false), exaone, bedrock))
                .isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있어도 Bedrock을 쓸 수 없으면 엑사원으로 답한다")
    void bedrock이_없으면_엑사원이다() {
        assertThat(RagAnswerLlmConfiguration.answerClient(new RagAnswerProperties(true), exaone, null))
                .isSameAs(exaone);
    }

    @Test
    @DisplayName("플래그가 켜져 있고 Bedrock을 쓸 수 있으면 Bedrock 먼저, 실패하면 엑사원으로 대체한다")
    void 켜져_있으면_bedrock_먼저다() {
        assertThat(RagAnswerLlmConfiguration.answerClient(new RagAnswerProperties(true), exaone, bedrock))
                .isInstanceOf(FallbackLlmClient.class);
    }

    @Test
    @DisplayName("답변 생성은 Bedrock이 실패하면 스트리밍으로도 엑사원이 답한다")
    void 실패하면_스트리밍도_엑사원이다() {
        var client = RagAnswerLlmConfiguration.answerClient(new RagAnswerProperties(true), exaone, bedrock);
        LlmStreamHandler handler = mock(LlmStreamHandler.class);
        doAnswer(invocation -> {
            invocation.<LlmStreamHandler>getArgument(1).onError(new IllegalStateException("bedrock down"));
            return null;
        }).when(bedrock).stream(any(), any());
        LlmRequest request = LlmRequest.builder().systemPrompt("s").userPrompt("u").build();

        client.stream(request, handler);

        verify(exaone).stream(request, handler);
        verify(handler, never()).onError(any());
    }

    @Test
    @DisplayName("설정을 주지 않으면 켜져 있고, 설정 이름으로 끈다")
    void 기본은_켜져_있고_설정으로_끈다() {
        assertThat(bind(Map.of()).bedrock()).isTrue();
        assertThat(bind(Map.of("rag.answer.bedrock", "false")).bedrock()).isFalse();
    }

    @Test
    @DisplayName("금액 재시도는 기본 켜짐이고 설정 이름으로 끈다")
    void 금액_재시도는_기본_켜짐() {
        assertThat(bind(Map.of()).amountRetry()).isTrue();
        assertThat(bind(Map.of("rag.answer.amount-retry", "false")).amountRetry()).isFalse();
        assertThat(new RagAnswerProperties(true).amountRetry()).isTrue();
    }

    @Test
    @DisplayName("금액 다시 생성은 대체 없이 gpt-oss만 부르고, 답변이 엑사원이거나 설정이 꺼져 있으면 하지 않는다")
    void 금액_다시_생성은_gpt_oss만_부른다() {
        assertThat(RagAnswerLlmConfiguration.amountRetryClient(new RagAnswerProperties(true, true), bedrock))
                .isSameAs(bedrock);
        assertThat(RagAnswerLlmConfiguration.amountRetryClient(new RagAnswerProperties(true, false), bedrock))
                .isNull();
        assertThat(RagAnswerLlmConfiguration.amountRetryClient(new RagAnswerProperties(false, true), bedrock))
                .isNull();
        assertThat(RagAnswerLlmConfiguration.amountRetryClient(new RagAnswerProperties(true, true), null))
                .isNull();
    }

    private static RagAnswerProperties bind(Map<String, Object> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("rag.answer", RagAnswerProperties.class);
    }
}
