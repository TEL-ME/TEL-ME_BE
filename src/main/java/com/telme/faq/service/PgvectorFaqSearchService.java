package com.telme.faq.service;

import com.telme.faq.config.EmbeddingProperties;
import com.telme.faq.config.FaqEmbeddingTextProperties;
import com.telme.faq.config.SearchProperties;
import com.telme.faq.config.SearchRerankProperties;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.entity.Faq;
import com.telme.faq.repository.FaqEmbeddingRepository;
import com.telme.faq.repository.FaqNearestMatch;
import com.telme.global.common.TimeZones;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PgvectorFaqSearchService implements FaqSearchService {

    private final EmbeddingClient embeddingClient;
    private final FaqEmbeddingRepository repository;
    private final EmbeddingProperties embeddingProperties;
    private final SearchProperties searchProperties;
    private final FaqEmbeddingTextProperties embeddingTextProperties;
    // search.rerank.enabled=true일 때만 빈이 있다. 없으면 null이고 기존 벡터 검색만 쓴다
    private final FaqReranker reranker;
    private final SearchRerankProperties rerankProperties;

    public PgvectorFaqSearchService(
            EmbeddingClient embeddingClient,
            FaqEmbeddingRepository repository,
            EmbeddingProperties embeddingProperties,
            SearchProperties searchProperties,
            FaqEmbeddingTextProperties embeddingTextProperties
    ) {
        this(embeddingClient, repository, embeddingProperties, searchProperties, embeddingTextProperties,
                (FaqReranker) null, null);
    }

    @Autowired
    public PgvectorFaqSearchService(
            EmbeddingClient embeddingClient,
            FaqEmbeddingRepository repository,
            EmbeddingProperties embeddingProperties,
            SearchProperties searchProperties,
            FaqEmbeddingTextProperties embeddingTextProperties,
            ObjectProvider<FaqReranker> reranker,
            SearchRerankProperties rerankProperties
    ) {
        this(embeddingClient, repository, embeddingProperties, searchProperties, embeddingTextProperties,
                reranker.getIfAvailable(), rerankProperties);
    }

    PgvectorFaqSearchService(
            EmbeddingClient embeddingClient,
            FaqEmbeddingRepository repository,
            EmbeddingProperties embeddingProperties,
            SearchProperties searchProperties,
            FaqEmbeddingTextProperties embeddingTextProperties,
            FaqReranker reranker,
            SearchRerankProperties rerankProperties
    ) {
        this.embeddingClient = embeddingClient;
        this.repository = repository;
        this.embeddingProperties = embeddingProperties;
        this.searchProperties = searchProperties;
        this.embeddingTextProperties = embeddingTextProperties;
        this.reranker = reranker;
        this.rerankProperties = rerankProperties;
    }

    @Override
    public List<FaqSearchResponse> search(FaqSearchRequest request) {
        float[] queryVector = embeddingClient.embed(request.query());
        if (reranker != null) {
            return searchWithRerank(request, queryVector);
        }
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

    // 코사인 점수는 정답과 무관 질문의 분포가 겹쳐 관련성 판정에 쓸 수 없다. 벡터는 후보만 넓게 모으고 판정은 리랭커 점수로 한다.
    // 실측(이중 벡터 도입 전)은 Q_A 후보만 리랭커에 넣은 구성이라, 질문만 벡터 후보는 섞지 않는다
    private List<FaqSearchResponse> searchWithRerank(FaqSearchRequest request, float[] queryVector) {
        List<FaqNearestMatch> candidates = repository.findNearest(
                queryVector, Math.max(request.topK(), rerankProperties.candidateK()), embeddingProperties.model());
        List<Faq> pool = candidates.stream()
                .filter(candidate -> 1 - candidate.distance() >= rerankProperties.vectorFloor())
                .map(candidate -> candidate.embedding().getFaq())
                .toList();
        if (pool.isEmpty()) {
            return List.of();
        }

        long started = System.nanoTime();
        double[] scores = reranker.score(
                request.query(), pool.stream().map(faq -> faq.getQuestion() + " " + faq.getAnswer()).toList());
        log.debug("[PgvectorFaqSearchService] 리랭크 {}건 {}ms", pool.size(), (System.nanoTime() - started) / 1_000_000);

        List<Integer> order = IntStream.range(0, pool.size())
                .boxed()
                .filter(i -> scores[i] >= rerankProperties.threshold())
                .sorted(Comparator.comparingDouble((Integer i) -> scores[i]).reversed())
                .limit(request.topK())
                .toList();

        List<FaqSearchResponse> results = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            Faq faq = pool.get(order.get(i));
            // 점수가 코사인이 아니라 리랭커 확률이라 어느 벡터로 찾았는지(matchedVariant)는 비운다
            results.add(toResponse(faq, scores[order.get(i)], i + 1, null));
        }
        return results;
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
            results.add(toResponse(candidate.embedding().getFaq(), score, rank, variant.name()));
        }
        return results;
    }

    private FaqSearchResponse toResponse(Faq faq, double score, int rank, String matchedVariant) {
        return new FaqSearchResponse(
                faq.getFaqId(),
                faq.getSlotId(),
                faq.getCategory(),
                faq.getQuestion(),
                faq.getAnswer(),
                score,
                faq.getVersion(),
                faq.getUpdatedAt().atZone(TimeZones.KST).toLocalDate(),
                rank,
                matchedVariant);
    }
}
