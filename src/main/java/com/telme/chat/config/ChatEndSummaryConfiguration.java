package com.telme.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.ChatEndSummaryModel;
import com.telme.chat.service.ChatEndSummaryModelClient;
import com.telme.llm.config.BedrockProperties;
import com.telme.llm.config.LlmProperties;
import com.telme.llm.converter.BedrockRequestConverter;
import com.telme.llm.converter.OllamaRequestConverter;
import com.telme.llm.service.BedrockClient;
import com.telme.llm.service.BedrockTransportClientBuilder;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.OllamaClient;
import com.telme.llm.service.RecordingLlmClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ChatEndSummaryProperties.class, ChatEndSummaryModelProperties.class})
class ChatEndSummaryConfiguration {
    private static final String SUMMARY_RUNTIME = "chatEndSummaryBedrockRuntime";

    @Bean(destroyMethod = "close", defaultCandidate = false)
    @Lazy
    BedrockRuntimeClient chatEndSummaryBedrockRuntime(ChatEndSummaryModelProperties properties) {
        return BedrockRuntimeClient.builder()
                .region(Region.of(properties.region()))
                .httpClientBuilder(new BedrockTransportClientBuilder(Apache5HttpClient.builder().maxConnections(2)
                        .connectionAcquisitionTimeout(Duration.ofSeconds(1))))
                .overrideConfiguration(config -> config.apiCallTimeout(properties.timeout())
                        .retryStrategy(AwsRetryStrategy.doNotRetry()))
                .build();
    }

    @Bean
    ChatEndSummaryModel chatEndSummaryModel(ChatEndSummaryProperties summary,
            ChatEndSummaryModelProperties properties, LlmClient primary, LlmGenerationRecorder recorder,
            @Qualifier(SUMMARY_RUNTIME) ObjectProvider<BedrockRuntimeClient> runtime, RestClient.Builder builder,
            ObjectMapper mapper, Clock clock, @Value("${ollama.url}") String ollamaUrl) {
        if (!summary.enabled() || properties.provider() == ChatEndSummaryModelProperties.Provider.PRIMARY) {
            return (request, deadline) -> primary.generate(request);
        }
        var ollama = createOllamaClient(properties, builder, mapper, recorder, ollamaUrl);
        if (properties.provider() == ChatEndSummaryModelProperties.Provider.OLLAMA) {
            return (request, deadline) -> ollama.generate(request);
        }
        var bedrockProperties = new BedrockProperties(true, properties.region(), properties.model(),
                properties.timeout(), properties.reasoningEffort(), summary.maxOutputTokens(), 2,
                Duration.ofSeconds(1));
        var bedrock = new RecordingLlmClient(new BedrockClient(runtime.getObject(),
                new BedrockRequestConverter(bedrockProperties), true), recorder, properties.model(), "bedrock");
        return new ChatEndSummaryModelClient(bedrock, ollama, clock, properties.timeout(),
                properties.fallbackConnectTimeout().plus(properties.fallbackReadTimeout()));
    }

    private RecordingLlmClient createOllamaClient(ChatEndSummaryModelProperties properties, RestClient.Builder builder,
            ObjectMapper mapper, LlmGenerationRecorder recorder, String ollamaUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.fallbackConnectTimeout());
        factory.setReadTimeout(properties.fallbackReadTimeout());
        var restClient = builder.clone().baseUrl(ollamaUrl).requestFactory(factory).build();
        var llmProperties = new LlmProperties(properties.fallbackModel(), properties.fallbackConnectTimeout(),
                properties.fallbackReadTimeout(), properties.fallbackContextSize());
        var client = new OllamaClient(restClient, new OllamaRequestConverter(llmProperties), mapper);
        return new RecordingLlmClient(client, recorder, properties.fallbackModel(), "ollama");
    }
}
