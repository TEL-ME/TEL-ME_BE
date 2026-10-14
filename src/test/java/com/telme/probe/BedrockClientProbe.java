package com.telme.probe;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.llm.config.BedrockProperties;
import com.telme.llm.converter.BedrockRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.BedrockClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

// 실제 Bedrock을 한 번 호출해 자격 증명·모델 접근·응답 해석을 확인한다(약 0.2원)
// TELME_PROBE=true이고 AWS_BEARER_TOKEN_BEDROCK 또는 AWS 기본 자격 증명이 있을 때만 돈다. CI에서는 돌지 않는다
@EnabledIfEnvironmentVariable(named = "TELME_PROBE", matches = "true")
class BedrockClientProbe {

    private static final String SYSTEM = """
            고객 질문에 답할 정보가 FAQ에 있는지 판단하십시오.
            JSON만 출력하십시오. {"answerable": true 또는 false}
            """;

    @Test
    void 실제_Bedrock_판정_호출() {
        BedrockProperties properties = new BedrockProperties(
                true, "us-east-1", "openai.gpt-oss-120b-1:0", Duration.ofSeconds(15), "low", 1000);
        try (BedrockRuntimeClient runtime = BedrockRuntimeClient.builder()
                .region(Region.of(properties.region()))
                .build()) {
            BedrockClient client = new BedrockClient(runtime, new BedrockRequestConverter(properties));
            LlmRequest request = LlmRequest.builder()
                    .taskType(TaskType.ROUTING)
                    .systemPrompt(SYSTEM)
                    .userPrompt("[FAQ]\n[1] Q: 유심 재발급 비용이 얼마예요?\nA: 유심 재발급 비용은 7,700원입니다.\n\n"
                            + "[고객 질문]\n유심 다시 받으려면 돈 얼마 들어요?")
                    .build();

            long started = System.currentTimeMillis();
            String text = client.generate(request);
            System.out.printf("[BedrockClientProbe] %dms 응답: %s%n", System.currentTimeMillis() - started, text);

            assertThat(text).contains("answerable");
        }
    }
}
