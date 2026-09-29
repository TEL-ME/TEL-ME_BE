package com.telme.faq.service;

import com.telme.faq.config.EmbeddingProperties;
import com.telme.faq.config.FaqEmbeddingTextProperties;
import com.telme.faq.config.SearchProperties;
import com.telme.faq.dto.req.FaqSearchRequest;
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
        List<FaqNearestMatch> candidates =
                repository.findNearest(queryVector, request.topK(), embeddingProperties.model());
        // faq_embeddings.embedding은 faq.embedding-text.variant 구성으로 만든 벡터다
        List<FaqSearchResponse> results =
                toResponses(candidates, searchProperties.similarityThreshold(), embeddingTextProperties.variant());

        SearchProperties.DualVector dualVector = searchProperties.dualVector();
        if (!dualVector.enabled()) {
            return results;
        }
        // 같은 질의 벡터로 질문만 벡터를 한 번 더 조회한다. 임베딩 호출은 그대로이고 DB 조회만 한 번 늘어난다
        List<FaqNearestMatch> questionCandidates =
                repository.findNearestByQuestionVector(queryVector, request.topK(), embeddingProperties.model());
        List<FaqSearchResponse> questionResults = toResponses(
                questionCandidates, dualVector.questionThreshold(), FaqEmbeddingTextVariant.QUESTION_ONLY);
        return DualVectorMerger.merge(questionResults, results, request.topK());
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
                    variant));
        }
        return results;
    }
}
