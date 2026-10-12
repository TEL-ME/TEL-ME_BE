package com.telme.consult.service;

import com.telme.consult.service.ConsultChatProcessingService.NoAnswerSuggestions;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.req.FaqSearchVector;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 근거가 없어 답을 못 한 질문에 가까운 FAQ 질문 1개를 고른다. LLM을 쓰지 않는다.
 *
 * <p>사용자 문장으로 질문+답변 벡터(Q_A) 후보를 찾아 점수가 하한 이상인 첫 FAQ를 쓴다. 2·3위 후보는 대부분
 * 엉뚱해서 1개만 보여 준다. 질문을 그대로 보내도 답이 안 나오는 FAQ는 버튼을 눌러도 답이 없으므로 건너뛰고
 * 다음 후보를 본다. 설계·측정: docs/FOLLOWUP_RECOMMENDATION.md 11절
 */
@Slf4j
public final class FaqCandidateNoAnswerSuggestions implements NoAnswerSuggestions {
    // 완전 무관 질문의 Q_A 최고 점수는 0.59 이하였다. 통신 주변 무관 질문은 이 하한으로 거르지 못한다(11.1절)
    static final double MIN_SCORE = 0.60;
    private static final int CANDIDATE_TOP_K = 10;

    private final FaqSearchService searches;
    private final SuggestedQuestionRecommender recommender;

    public FaqCandidateNoAnswerSuggestions(FaqSearchService searches, SuggestedQuestionRecommender recommender) {
        this.searches = Objects.requireNonNull(searches);
        this.recommender = Objects.requireNonNull(recommender);
    }

    @Override
    public List<String> suggest(String userQuestion) {
        return firstCandidate(userQuestion, false).map(List::of).orElse(List.of());
    }

    /**
     * 질문과 가까운 FAQ의 현재 질문 하나. skipTrouble이면 문제 상황(TROUBLE) FAQ도 건너뛴다.
     * 복합 질문을 나눠 보낼 버튼처럼 사용자 상황을 모르는 곳에서는 상황이 섞인 질문이 어색해서 뺀다
     */
    Optional<String> firstCandidate(String query, boolean skipTrouble) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }
        // 버튼은 부가 정보라 실패해도 답변은 그대로 저장한다
        try {
            // 검색이 숨김·미동기화 FAQ를 빼고 현재 질문을 주므로 버튼을 누르면 같은 FAQ가 다시 검색된다
            return searches.searchCandidates(
                            new FaqSearchRequest(query, CANDIDATE_TOP_K, FaqSearchVector.QA)).stream()
                    .filter(candidate -> candidate.score() >= MIN_SCORE)
                    .filter(candidate -> !recommender.isUnanswerable(candidate.slotId()))
                    .filter(candidate -> !skipTrouble || !recommender.isTrouble(candidate.slotId()))
                    .map(FaqSearchResponse::question)
                    .filter(question -> question != null && !question.isBlank())
                    .findFirst();
        } catch (RuntimeException e) {
            log.warn("FAQ 후보 찾기 실패", e);
            return Optional.empty();
        }
    }
}
