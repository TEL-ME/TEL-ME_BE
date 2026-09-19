package com.telme.faq.service;

import com.telme.faq.config.EmbeddingProperties;
import com.telme.faq.dto.req.EmbedRequest;
import com.telme.faq.dto.res.EmbedResponse;
import com.telme.faq.exception.FaqErrorCode;
import com.telme.global.common.exception.GeneralException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class EmbeddingClient {

    private final RestClient searchClient;
    private final RestClient batchClient;
    private final EmbeddingProperties properties;

    public EmbeddingClient(
            @Qualifier("embeddingSearchClient") RestClient searchClient,
            @Qualifier("embeddingBatchClient") RestClient batchClient,
            EmbeddingProperties properties
    ) {
        this.searchClient = searchClient;
        this.batchClient = batchClient;
        this.properties = properties;
    }

    // 단건 — 질문 1개 임베딩 (검색용, 짧은 타임아웃)
    public float[] embed(String text) {
        if (isBlank(text)) {
            throw new GeneralException(FaqErrorCode.EMBEDDING_REQUEST_INVALID);
        }

        List<String> texts = List.of(text);
        return validate(texts, call(searchClient, texts)).get(0);
    }

    // 배치 — 여러 텍스트를 한 번에 임베딩 (FAQ 적재용, 긴 타임아웃)
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty() || texts.stream().anyMatch(EmbeddingClient::isBlank)) {
            throw new GeneralException(FaqErrorCode.EMBEDDING_REQUEST_INVALID);
        }

        return validate(texts, call(batchClient, texts));
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private EmbedResponse call(RestClient client, List<String> texts) {
        try {
            return client.post()
                    .uri("/api/embed")
                    .body(new EmbedRequest(properties.model(), texts))
                    .retrieve()
                    .body(EmbedResponse.class);
        } catch (HttpClientErrorException e) {
            log.warn("[EmbeddingClient] Ollama가 요청을 거부했습니다: {}", e.getStatusCode(), e);
            throw new GeneralException(FaqErrorCode.EMBEDDING_REQUEST_REJECTED);
        } catch (RestClientException e) {
            log.warn("[EmbeddingClient] Ollama 호출에 실패했습니다.", e);
            throw new GeneralException(FaqErrorCode.EMBEDDING_REQUEST_FAILED);
        }
    }

    List<float[]> validate(List<String> texts, EmbedResponse response) {
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
            if (vector.length != properties.dimension()) {
                log.warn("[EmbeddingClient] 벡터 차원이 {}가 아닙니다: {}", properties.dimension(), vector.length);
                throw new GeneralException(FaqErrorCode.EMBEDDING_RESPONSE_INVALID);
            }
        }

        return embeddings;
    }
}
