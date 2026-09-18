package com.telme.llm.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmGenerationRecorder;
import com.telme.llm.service.RecordingLlmClient;
import com.telme.llm.service.RetryingLlmClient;

@Configuration
@EnableConfigurationProperties({LlmProperties.class, LlmRetryProperties.class})
public class LlmConfig {

    @Bean
    public RestClient ollamaRestClient(
            RestClient.Builder builder,
            LlmProperties properties,
            @Value("${ollama.url}") String ollamaUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(ollamaUrl)
                .requestFactory(requestFactory)
                .build();
    }

    // Ollama 호출만 재시도·기록으로 감싼다. Fake는 실패하지 않고 기록할 값도 없어 감싸지 않는다
    // 기록이 가장 바깥이라 재시도까지 포함한 전체 시간이 남는다
    @Bean
    @Primary
    @ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
    public LlmClient ollamaLlmClient(
            @Qualifier("baseLlmClient") LlmClient baseLlmClient,
            LlmProperties properties,
            LlmRetryProperties retryProperties,
            LlmGenerationRecorder recorder) {
        return new RecordingLlmClient(
                new RetryingLlmClient(baseLlmClient, retryProperties), recorder, properties.model());
    }
}
