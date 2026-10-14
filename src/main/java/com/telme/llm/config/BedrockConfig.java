package com.telme.llm.config;

import com.telme.llm.converter.BedrockRequestConverter;
import com.telme.llm.service.BedrockClient;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.RecordingLlmClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

// 검색 근거 판정 전용 LLM. 답변 생성용 LlmClient(@Primary Ollama, Fake)와 섞이지 않게
// 이름(JUDGE_LLM_CLIENT)으로만 주입받게 한다. llm.bedrock.enabled가 false면 빈을 만들지 않는다
@Configuration
@EnableConfigurationProperties(BedrockProperties.class)
public class BedrockConfig {

    public static final String JUDGE_LLM_CLIENT = "judgeLlmClient";

    // 자격 증명은 첫 호출 때 읽으므로 키가 없는 환경에서도 앱은 뜨고 판정 호출만 실패한다
    // 판정이 실패하면 호출하는 쪽이 정한 규칙으로 처리하므로 SDK 재시도는 끈다. 재시도하면 지연만 길어진다
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "llm.bedrock.enabled", havingValue = "true")
    public BedrockRuntimeClient bedrockRuntimeClient(BedrockProperties properties) {
        return BedrockRuntimeClient.builder()
                .region(Region.of(properties.region()))
                .overrideConfiguration(config -> config
                        .apiCallTimeout(properties.timeout())
                        .retryStrategy(AwsRetryStrategy.doNotRetry()))
                .build();
    }

    // 호출마다 llm_generations에 모델·지연·결과를 남긴다(executionId가 있을 때만)
    @Bean(name = JUDGE_LLM_CLIENT, defaultCandidate = false)
    @ConditionalOnProperty(name = "llm.bedrock.enabled", havingValue = "true")
    public LlmClient judgeLlmClient(
            BedrockRuntimeClient bedrockRuntimeClient,
            BedrockProperties properties,
            LlmGenerationRecorder recorder) {
        return new RecordingLlmClient(
                new BedrockClient(bedrockRuntimeClient, new BedrockRequestConverter(properties)),
                recorder, properties.model(), "bedrock");
    }
}
