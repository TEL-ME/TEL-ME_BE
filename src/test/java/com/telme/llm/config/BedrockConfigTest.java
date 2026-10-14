package com.telme.llm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.LlmStreamHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

class BedrockConfigTest {

    // 답변 생성용 LlmClient가 하나 있는 상태(Fake 또는 @Primary Ollama)를 흉내 낸다
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(BedrockConfig.class)
            .withBean(LlmGenerationRecorder.class, () -> mock(LlmGenerationRecorder.class))
            .withBean("answerLlmClient", LlmClient.class, AnswerClient::new);

    @Test
    @DisplayName("기본은 꺼져 있어 Bedrock 빈을 만들지 않고, 키가 없어도 뜬다")
    void 기본은_꺼져_있다() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(BedrockRuntimeClient.class);
            assertThat(context).doesNotHaveBean(BedrockConfig.JUDGE_LLM_CLIENT);
        });
    }

    @Test
    @DisplayName("켜면 판정 전용 빈을 만들지만, 타입으로 LlmClient를 받는 곳에는 들어가지 않는다")
    void 켜도_답변용_LlmClient를_대신하지_않는다() {
        runner.withPropertyValues("llm.bedrock.enabled=true", "llm.bedrock.region=us-east-1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean(BedrockConfig.JUDGE_LLM_CLIENT);
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(AnswerClient.class);
                });
    }

    private static final class AnswerClient implements LlmClient {

        @Override
        public String generate(LlmRequest request) {
            return "답변";
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            handler.onComplete();
        }
    }
}
