package com.telme.faq.service;

import com.telme.faq.config.EmbeddingProperties;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.entity.Faq;
import com.telme.faq.entity.FaqEmbedding;
import com.telme.faq.repository.FaqEmbeddingRepository;
import com.telme.global.common.TimeZones;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PgvectorFaqSearchService implements FaqSearchService {

    private final EmbeddingClient embeddingClient;
    private final FaqEmbeddingRepository repository;
    private final EmbeddingProperties embeddingProperties;
    private final double similarityThreshold;

    public PgvectorFaqSearchService(
            EmbeddingClient embeddingClient,
            FaqEmbeddingRepository repository,
            EmbeddingProperties embeddingProperties,
            @Value("${search.similarity-threshold}") double similarityThreshold
    ) {
        this.embeddingClient = embeddingClient;
        this.repository = repository;
        this.embeddingProperties = embeddingProperties;
        this.similarityThreshold = similarityThreshold;
    }

    @Override
    public List<FaqSearchResponse> search(FaqSearchRequest request) {
        float[] queryVector = embeddingClient.embed(request.query());
        List<FaqEmbedding> candidates =
                repository.findNearest(queryVector, request.topK(), embeddingProperties.model());

        if (candidates.isEmpty()) {
            return List.of();
        }

        // repository가 이미 거리순(=유사도 내림차순)으로 정렬해서 반환하므로
        // 임계값 미만이 한 번 나오면 그 뒤로는 전부 미달 — 그 지점에서 끊음
        // 호출하는 쪽이 score를 재판정하지 않아도 되도록(FaqSearchService 계약) 항목별로 거름
        List<FaqSearchResponse> results = new ArrayList<>();
        int rank = 0;
        for (FaqEmbedding candidate : candidates) {
            double score = cosineSimilarity(queryVector, candidate.getEmbedding());
            // 쿼리 또는 후보가 영벡터이면 분모가 0이 되어 score가 NaN
            if (!Double.isFinite(score) || score < similarityThreshold) {
                break;
            }
            rank++;
            Faq faq = candidate.getFaq();
            results.add(new FaqSearchResponse(
                    faq.getFaqId(),
                    faq.getCategory(),
                    faq.getQuestion(),
                    faq.getAnswer(),
                    score,
                    faq.getVersion(),
                    faq.getUpdatedAt().atZone(TimeZones.KST).toLocalDate(),
                    rank));
        }
        return results;
    }

    // topK(최대 수 건)에 대해서만 계산하므로
    // DB에서 같이 뽑는 대신 애플리케이션에서 직접 계산
    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
