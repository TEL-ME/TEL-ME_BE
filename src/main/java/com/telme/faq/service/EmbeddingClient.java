package com.telme.faq.service;

import com.telme.faq.exception.FaqErrorCode;
import com.telme.global.common.exception.GeneralException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class EmbeddingClient {

    private static final String MODEL = "bge-m3";
    private static final int EMBEDDING_DIMENSION = 1024;

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration SEARCH_READ_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration BATCH_READ_TIMEOUT = Duration.ofSeconds(120);

    private final RestClient searchClient;
    private final RestClient batchClient;

    public EmbeddingClient(@Value("${OLLAMA_URL:http://localhost:11434}") String ollamaUrl) {
        this.searchClient = buildClient(ollamaUrl, SEARCH_READ_TIMEOUT);
        this.batchClient = buildClient(ollamaUrl, BATCH_READ_TIMEOUT);
    }

    private RestClient buildClient(String ollamaUrl, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(readTimeout);

        return RestClient.builder()
                .baseUrl(ollamaUrl)
                .requestFactory(factory)
                .build();
    }

    // 단건 — 질문 1개 임베딩 (검색용, 짧은 타임아웃)
    public float[] embed(String text) {
        List<String> texts = List.of(text);
        EmbedResponse response = searchClient.post()
                .uri("/api/embed")
                .body(new EmbedRequest(MODEL, texts))
                .retrieve()
                .body(EmbedResponse.class);

        return validate(texts, response).get(0);
    }

    // 배치 — 여러 텍스트를 한 번에 임베딩 (FAQ 적재용, 긴 타임아웃)
    public List<float[]> embedBatch(List<String> texts) {
        EmbedResponse response = batchClient.post()
                .uri("/api/embed")
                .body(new EmbedRequest(MODEL, texts))
                .retrieve()
                .body(EmbedResponse.class);

        return validate(texts, response);
    }

    // 상세 사유는 로그로 남기고, 클라이언트에는 공통 에러 코드(FaqErrorCode)로 응답
    private List<float[]> validate(List<String> texts, EmbedResponse response) {
        if (response == null || response.embeddings() == null) {
            log.warn("[EmbeddingClient] Ollama 응답이 비어있습니다.");
            throw new GeneralException(FaqErrorCode.EMBEDDING_RESPONSE_INVALID);
        }

        List<float[]> embeddings = response.embeddings();
        if (embeddings.size() != texts.size()) {
            log.warn("[EmbeddingClient] 요청 개수({})와 응답 벡터 개수({})가 다릅니다.", texts.size(), embeddings.size());
            throw new GeneralException(FaqErrorCode.EMBEDDING_RESPONSE_INVALID);
        }

        for (float[] vector : embeddings) {
            if (vector == null) {
                log.warn("[EmbeddingClient] 응답에 빈(null) 벡터가 포함되어 있습니다.");
                throw new GeneralException(FaqErrorCode.EMBEDDING_RESPONSE_INVALID);
            }
            if (vector.length != EMBEDDING_DIMENSION) {
                log.warn("[EmbeddingClient] 벡터 차원이 {}가 아닙니다: {}", EMBEDDING_DIMENSION, vector.length);
                throw new GeneralException(FaqErrorCode.EMBEDDING_RESPONSE_INVALID);
            }
        }

        return embeddings;
    }

    record EmbedRequest(String model, List<String> input) {
    }

    record EmbedResponse(List<float[]> embeddings) {
    }
}
