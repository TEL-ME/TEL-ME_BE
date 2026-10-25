package com.telme.faq.service;

import com.telme.faq.config.EmbeddingProperties;
import com.telme.faq.config.FaqEmbeddingTextProperties;
import com.telme.faq.config.SearchProperties;
import com.telme.faq.dto.req.FaqSearchKind;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.req.FaqSearchVector;
import com.telme.faq.dto.res.FaqGrayZoneSearchResponse;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.entity.Faq;
import com.telme.faq.entity.FaqGrayZoneOutcome;
import com.telme.faq.repository.FaqEmbeddingRepository;
import com.telme.faq.repository.FaqNearestMatch;
import com.telme.faq.repository.FaqSearchScoreRepository;
import com.telme.global.common.TimeZones;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PgvectorFaqSearchService implements FaqSearchService {

    private final EmbeddingClient embeddingClient;
    private final FaqEmbeddingRepository repository;
    private final EmbeddingProperties embeddingProperties;
    private final SearchProperties searchProperties;
    private final FaqEmbeddingTextProperties embeddingTextProperties;
    private final FaqSearchScoreRepository scoreRepository;

    public PgvectorFaqSearchService(
            EmbeddingClient embeddingClient,
            FaqEmbeddingRepository repository,
            EmbeddingProperties embeddingProperties,
            SearchProperties searchProperties,
            FaqEmbeddingTextProperties embeddingTextProperties,
            FaqSearchScoreRepository scoreRepository
    ) {
        this.embeddingClient = embeddingClient;
        this.repository = repository;
        this.embeddingProperties = embeddingProperties;
        this.searchProperties = searchProperties;
        this.embeddingTextProperties = embeddingTextProperties;
        this.scoreRepository = scoreRepository;
    }

    @Override
    public List<FaqSearchResponse> search(FaqSearchRequest request) {
        float[] queryVector = embeddingClient.embed(request.query());
        int topK = request.topK();
        FaqSearchVector vector = vectorOf(request);
        // 같은 질의 벡터로 두 벡터를 조회한다. 이중 벡터여도 임베딩 호출은 그대로이고 DB 조회만 한 번 늘어난다
        Searched qa = vector == FaqSearchVector.QUESTION ? null : searchQa(queryVector, topK);
        List<FaqSearchResponse> results = switch (vector) {
            case QA -> qa.results();
            case QUESTION -> searchQuestion(queryVector, topK);
            case DUAL -> DualVectorMerger.merge(searchQuestion(queryVector, topK), qa.results(), topK);
        };
        recordScore(request.kind(), qa == null ? null : qa.topScore(), !results.isEmpty());
        return results;
    }

    // 벡터별 상위 topK를 회색 구간 선으로 나눈다. 아래 선 미만은 빼고, 통과 선 미만은 판정 필요로 표시한다
    @Override
    public FaqGrayZoneSearchResponse searchGrayZone(FaqSearchRequest request) {
        SearchProperties.GrayZone zone = searchProperties.grayZone();
        float[] queryVector = embeddingClient.embed(request.query());
        int topK = request.topK();
        FaqSearchVector vector = vectorOf(request);
        List<FaqNearestMatch> qa = vector == FaqSearchVector.QUESTION
                ? List.of() : repository.findNearest(queryVector, topK, embeddingProperties.model());
        List<FaqNearestMatch> question = vector == FaqSearchVector.QA
                ? List.of() : repository.findNearestByQuestionVector(queryVector, topK, embeddingProperties.model());
        // 관리자 점수 분포는 판정 전 기존 임계값 기준으로 남겨 켜기 전과 같은 지표로 비교한다
        boolean passedBefore = passes(qa, searchProperties.similarityThreshold())
                || passes(question, searchProperties.dualVector().questionThreshold());
        Long searchScoreId = recordScore(request.kind(), topScore(qa), passedBefore);
        return new FaqGrayZoneSearchResponse(
                toCandidates(question, zone.questionPass(), zone.questionFloor(), FaqEmbeddingTextVariant.QUESTION_ONLY),
                toCandidates(qa, zone.qaPass(), zone.qaFloor(), embeddingTextProperties.variant()),
                searchScoreId);
    }

    // 기록 실패가 답변을 막지 않도록 recordScore처럼 여기서 삼킨다
    @Override
    public void recordGrayZoneOutcome(Long searchScoreId, FaqGrayZoneOutcome outcome) {
        if (searchScoreId == null || outcome == null) {
            return;
        }
        try {
            scoreRepository.markOutcome(searchScoreId, outcome);
        } catch (RuntimeException e) {
            log.warn("[PgvectorFaqSearchService] 검색 판정 결과 기록 실패 searchScoreId={}", searchScoreId, e);
        }
    }

    private List<FaqGrayZoneSearchResponse.Candidate> toCandidates(
            List<FaqNearestMatch> matches, double pass, double floor, FaqEmbeddingTextVariant variant) {
        return toResponses(matches, floor, variant).stream()
                .map(result -> new FaqGrayZoneSearchResponse.Candidate(result, result.score() < pass))
                .toList();
    }

    private boolean passes(List<FaqNearestMatch> matches, double threshold) {
        return !matches.isEmpty() && 1 - matches.get(0).distance() >= threshold;
    }
    
    // 임계값 미달 후보는 결과에서 잘려 남지 않아, 자르기 전 1위 점수를 관리자 분포용으로 남긴다.
    // 기록 실패가 검색을 막지 않도록 여기서 삼킨다. 기록 번호를 돌려주고, 기록하지 않았으면 null
    private Long recordScore(FaqSearchKind kind, Double qaTopScore, boolean passed) {
        if (kind == null || !searchProperties.scoreRecording().enabled()) {
            return null;
        }
        try {
            return scoreRepository.save(kind, qaTopScore, passed, searchProperties.similarityThreshold());
        } catch (RuntimeException e) {
            log.warn("[PgvectorFaqSearchService] 검색 점수 기록 실패", e);
            return null;
        }
    }
    
    // faq_embeddings.embedding은 faq.embedding-text.variant 구성으로 만든 벡터다
    private Searched searchQa(float[] queryVector, int topK) {
        List<FaqNearestMatch> candidates = repository.findNearest(queryVector, topK, embeddingProperties.model());
        return new Searched(toResponses(candidates, searchProperties.similarityThreshold(),
                embeddingTextProperties.variant()), topScore(candidates));
    }
    
    @Override
    public List<FaqSearchResponse> searchCandidates(FaqSearchRequest request) {
        float[] queryVector = embeddingClient.embed(request.query());
        int topK = request.topK();
        return switch (vectorOf(request)) {
        case QA -> searchQaCandidates(queryVector, topK);
        case QUESTION -> searchQuestionCandidates(queryVector, topK);
        case DUAL -> DualVectorMerger.merge(
                searchQuestionCandidates(queryVector, topK), searchQaCandidates(queryVector, topK), topK);
        };
    }

    // repository가 거리순으로 정렬해 반환하므로 첫 후보가 1위다. 후보가 없거나 점수가 숫자가 아니면 null
    private Double topScore(List<FaqNearestMatch> candidates) {
        if (candidates.isEmpty()) {
            return null;
        }
        double score = 1 - candidates.get(0).distance();
        return Double.isFinite(score) ? score : null;
    }

    // 요청이 벡터를 고르지 않으면(채팅·상담 경로) 설정을 따른다. 측정용 테스트 API만 벡터를 고른다
    private FaqSearchVector vectorOf(FaqSearchRequest request) {
        if (request.vector() != null) {
            return request.vector();
        }
        return searchProperties.dualVector().enabled() ? FaqSearchVector.DUAL : FaqSearchVector.QA;
    }

    private List<FaqSearchResponse> searchQuestion(float[] queryVector, int topK) {
        List<FaqNearestMatch> candidates =
                repository.findNearestByQuestionVector(queryVector, topK, embeddingProperties.model());
        return toResponses(candidates, searchProperties.dualVector().questionThreshold(),
                FaqEmbeddingTextVariant.QUESTION_ONLY);
    }

    private List<FaqSearchResponse> searchQaCandidates(float[] queryVector, int topK) {
        List<FaqNearestMatch> candidates = repository.findNearest(queryVector, topK, embeddingProperties.model());
        return toResponses(candidates, Double.NEGATIVE_INFINITY, embeddingTextProperties.variant());
    }

    private List<FaqSearchResponse> searchQuestionCandidates(float[] queryVector, int topK) {
        List<FaqNearestMatch> candidates =
                repository.findNearestByQuestionVector(queryVector, topK, embeddingProperties.model());
        return toResponses(candidates, Double.NEGATIVE_INFINITY, FaqEmbeddingTextVariant.QUESTION_ONLY);
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
    
    private record Searched(List<FaqSearchResponse> results, Double topScore) {
    }
}
