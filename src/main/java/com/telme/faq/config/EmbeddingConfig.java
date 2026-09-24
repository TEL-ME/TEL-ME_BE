package com.telme.faq.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({
        EmbeddingProperties.class,
        FaqBatchLoadProperties.class,
        FaqEmbeddingTextProperties.class,
        FaqReembedProperties.class
})
public class EmbeddingConfig {

    @Bean
    public RestClient embeddingSearchClient(
            RestClient.Builder builder,
            EmbeddingProperties properties,
            @Value("${ollama.url}") String ollamaUrl
    ) {
        return build(builder, ollamaUrl, properties.connectTimeout(), properties.searchReadTimeout());
    }

    @Bean
    public RestClient embeddingBatchClient(
            RestClient.Builder builder,
            EmbeddingProperties properties,
            @Value("${ollama.url}") String ollamaUrl
    ) {
        return build(builder, ollamaUrl, properties.connectTimeout(), properties.batchReadTimeout());
    }

    private RestClient build(RestClient.Builder builder, String ollamaUrl, Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        return builder
                .baseUrl(ollamaUrl)
                .requestFactory(factory)
                .build();
    }
}
