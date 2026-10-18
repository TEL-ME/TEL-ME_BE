package com.telme.rag.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.telme.llm.service.FallbackLlmClient;
import com.telme.llm.service.LlmClient;
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
    @DisplayName("설정을 주지 않으면 꺼져 있고, 설정 이름으로 켠다")
    void 기본은_꺼져_있고_설정으로_켠다() {
        assertThat(bind(Map.of()).bedrock()).isFalse();
        assertThat(bind(Map.of("rag.answer.bedrock", "true")).bedrock()).isTrue();
    }

    private static RagAnswerProperties bind(Map<String, Object> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bindOrCreate("rag.answer", RagAnswerProperties.class);
    }
}
