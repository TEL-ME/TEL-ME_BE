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

    // Ollama 호출만 재시도로 감싼다. Fake는 실패하지 않아 감싸지 않는다
    @Bean
    @Primary
    @ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
    public LlmClient retryingLlmClient(
            @Qualifier("baseLlmClient") LlmClient baseLlmClient, LlmRetryProperties retryProperties) {
        return new RetryingLlmClient(baseLlmClient, retryProperties);
    }
}
