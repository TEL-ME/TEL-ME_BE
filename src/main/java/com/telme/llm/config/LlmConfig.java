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
    // 기록이 재시도 안쪽이라 시도마다 한 줄씩 남는다
    @Bean
    @Primary
    @ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
    public LlmClient ollamaLlmClient(
            @Qualifier("baseLlmClient") LlmClient baseLlmClient,
            LlmProperties properties,
            LlmRetryProperties retryProperties,
            LlmGenerationRecorder recorder) {
        return new RetryingLlmClient(
                new RecordingLlmClient(baseLlmClient, recorder, properties.model()), retryProperties);
    }

    // vLLM(OpenAI 호환 API) 서버. 시간 제한은 Ollama와 같은 llm.* 값을 쓴다
    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "vllm")
    public RestClient vllmRestClient(
            RestClient.Builder builder,
            LlmProperties properties,
            @Value("${vllm.url}") String vllmUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(vllmUrl)
                .requestFactory(requestFactory)
                .build();
    }

    // Ollama와 같은 재시도·기록으로 감싸고, 호출 기록에는 서빙을 구분하도록 provider를 vllm으로 남긴다
    @Bean
    @Primary
    @ConditionalOnProperty(name = "llm.provider", havingValue = "vllm")
    public LlmClient vllmLlmClient(
            @Qualifier("baseLlmClient") LlmClient baseLlmClient,
            LlmProperties properties,
            LlmRetryProperties retryProperties,
            LlmGenerationRecorder recorder) {
        return new RetryingLlmClient(
                new RecordingLlmClient(baseLlmClient, recorder, properties.model(), LlmGenerationRecorder.VLLM),
                retryProperties);
    }
}
