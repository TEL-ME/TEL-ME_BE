package com.telme.faq.service;

import com.telme.faq.config.EmbeddingProperties;
import com.telme.faq.config.FaqEmbeddingTextProperties;
import com.telme.faq.config.SearchProperties;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.req.FaqSearchVector;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.entity.Faq;
import com.telme.faq.repository.FaqEmbeddingRepository;
import com.telme.faq.repository.FaqNearestMatch;
import com.telme.global.common.TimeZones;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PgvectorFaqSearchService implements FaqSearchService {

    private final EmbeddingClient embeddingClient;
    private final FaqEmbeddingRepository repository;
    private final EmbeddingProperties embeddingProperties;
    private final SearchProperties searchProperties;
    private final FaqEmbeddingTextProperties embeddingTextProperties;

    public PgvectorFaqSearchService(
            EmbeddingClient embeddingClient,
            FaqEmbeddingRepository repository,
            EmbeddingProperties embeddingProperties,
            SearchProperties searchProperties,
            FaqEmbeddingTextProperties embeddingTextProperties
    ) {
        this.embeddingClient = embeddingClient;
        this.repository = repository;
        this.embeddingProperties = embeddingProperties;
        this.searchProperties = searchProperties;
        this.embeddingTextProperties = embeddingTextProperties;
    }

    @Override
    public List<FaqSearchResponse> search(FaqSearchRequest request) {
        float[] queryVector = embeddingClient.embed(request.query());
        int topK = request.topK();
        // 같은 질의 벡터로 두 벡터를 조회한다. 이중 벡터여도 임베딩 호출은 그대로이고 DB 조회만 한 번 늘어난다
        return switch (vectorOf(request)) {
            case QA -> searchQa(queryVector, topK);
            case QUESTION -> searchQuestion(queryVector, topK);
            case DUAL -> DualVectorMerger.merge(searchQuestion(queryVector, topK), searchQa(queryVector, topK), topK);
        };
    }

    // 요청이 벡터를 고르지 않으면(채팅·상담 경로) 설정을 따른다. 측정용 테스트 API만 벡터를 고른다
    private FaqSearchVector vectorOf(FaqSearchRequest request) {
        if (request.vector() != null) {
            return request.vector();
        }
        return searchProperties.dualVector().enabled() ? FaqSearchVector.DUAL : FaqSearchVector.QA;
    }

    // faq_embeddings.embedding은 faq.embedding-text.variant 구성으로 만든 벡터다
    private List<FaqSearchResponse> searchQa(float[] queryVector, int topK) {
        List<FaqNearestMatch> candidates = repository.findNearest(queryVector, topK, embeddingProperties.model());
        return toResponses(candidates, searchProperties.similarityThreshold(), embeddingTextProperties.variant());
    }

    private List<FaqSearchResponse> searchQuestion(float[] queryVector, int topK) {
        List<FaqNearestMatch> candidates =
                repository.findNearestByQuestionVector(queryVector, topK, embeddingProperties.model());
        return toResponses(candidates, searchProperties.dualVector().questionThreshold(),
                FaqEmbeddingTextVariant.QUESTION_ONLY);
    }

    // repository가 이미 거리순으로 정렬해 반환하므로, 임계값 미달이 한 번 나오면 그 지점에서 끊는다
    // 이중 벡터 검색에서는 벡터마다 임계값이 달라 threshold를 받는다
    private List<FaqSearchResponse> toResponses(
            List<FaqNearestMatch> candidates, double threshold, FaqEmbeddingTextVariant variant) {
        List<FaqSearchResponse> results = new ArrayList<>();
        int rank = 0;
        for (FaqNearestMatch candidate : candidates) {
            double score = 1 - candidate.distance();
            if (!Double.isFinite(score) || score < threshold) {
                break;
            }
            rank++;
            Faq faq = candidate.embedding().getFaq();
            results.add(new FaqSearchResponse(
                    faq.getFaqId(),
                    faq.getSlotId(),
                    faq.getCategory(),
                    faq.getQuestion(),
                    faq.getAnswer(),
                    score,
                    faq.getVersion(),
                    faq.getUpdatedAt().atZone(TimeZones.KST).toLocalDate(),
                    rank,
                    variant.name()));
        }
        return results;
    }
}
