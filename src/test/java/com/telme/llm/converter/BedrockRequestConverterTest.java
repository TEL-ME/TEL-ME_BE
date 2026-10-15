package com.telme.llm.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.llm.config.BedrockProperties;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;

class BedrockRequestConverterTest {

    private static BedrockProperties properties(String reasoningEffort) {
        return new BedrockProperties(true, "us-east-1", "openai.gpt-oss-120b-1:0",
                Duration.ofSeconds(5), reasoningEffort, 1000, 50, Duration.ofSeconds(1));
    }

    private final BedrockRequestConverter converter = new BedrockRequestConverter(properties("low"));

    @Test
    @DisplayName("규칙은 system, 질문은 user 메시지 하나로 담고 모델을 지정한다")
    void 규칙과_질문을_나눠_담는다() {
        ConverseRequest result = converter.toConverseRequest(LlmRequest.builder()
                .taskType(TaskType.ROUTING)
                .systemPrompt("FAQ로 답이 되는지 판단")
                .userPrompt("[FAQ] ... [고객 질문] ...")
                .build());

        assertThat(result.modelId()).isEqualTo("openai.gpt-oss-120b-1:0");
        assertThat(result.system()).singleElement().satisfies(block ->
                assertThat(block.text()).isEqualTo("FAQ로 답이 되는지 판단"));
        assertThat(result.messages()).singleElement().satisfies(message -> {
            assertThat(message.role()).isEqualTo(ConversationRole.USER);
            assertThat(message.content().getFirst().text()).isEqualTo("[FAQ] ... [고객 질문] ...");
        });
    }

    @Test
    @DisplayName("요청에 값이 없으면 temperature 0, 설정의 최대 토큰을 쓰고 생각 길이를 함께 보낸다")
    void 기본값과_생각_길이를_보낸다() {
        ConverseRequest result = converter.toConverseRequest(LlmRequest.builder()
                .taskType(TaskType.ROUTING).userPrompt("질문").build());

        assertThat(result.inferenceConfig().temperature()).isEqualTo(0.0f);
        assertThat(result.inferenceConfig().maxTokens()).isEqualTo(1000);
        assertThat(result.additionalModelRequestFields().asMap().get("reasoning_effort").asString())
                .isEqualTo("low");
    }

    @Test
    @DisplayName("요청에 지정한 temperature와 최대 토큰을 그대로 쓴다")
    void 요청값을_그대로_쓴다() {
        ConverseRequest result = converter.toConverseRequest(LlmRequest.builder()
                .taskType(TaskType.ROUTING).userPrompt("질문").temperature(0.2).maxTokens(300).build());

        assertThat(result.inferenceConfig().temperature()).isEqualTo(0.2f);
        assertThat(result.inferenceConfig().maxTokens()).isEqualTo(300);
    }

    @Test
    @DisplayName("system이 비었거나 생각 길이 설정이 없으면 보내지 않는다")
    void 빈_값은_보내지_않는다() {
        ConverseRequest result = new BedrockRequestConverter(properties("")).toConverseRequest(
                LlmRequest.builder().taskType(TaskType.ROUTING).systemPrompt(" ").userPrompt("질문").build());

        assertThat(result.system()).isEmpty();
        assertThat(result.additionalModelRequestFields()).isNull();
    }
}
