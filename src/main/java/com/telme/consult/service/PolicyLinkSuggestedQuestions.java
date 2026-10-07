package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.consult.service.RagSearchResultAnswerGenerator.SuggestedQuestions;
import com.telme.consult.service.SuggestedQuestionRecommender.BaseFaq;
import com.telme.faq.dto.res.FaqSearchResponse;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;

/**
 * 근거가 있는 답변(GROUNDED)에만 LLM에 넘긴 검색 결과 1순위 FAQ의 정책으로 추천 질문을 만든다.
 * 모델이 실제로 참고한 근거는 알 수 없어 1순위를 기준으로 쓰고, 2순위 이하는 보지 않는다.
 */
@Slf4j
public final class PolicyLinkSuggestedQuestions implements SuggestedQuestions {
    private final SuggestedQuestionRecommender recommender;
    private final PolicyRefFinder policyRefs;

    public PolicyLinkSuggestedQuestions(SuggestedQuestionRecommender recommender, PolicyRefFinder policyRefs) {
        this.recommender = Objects.requireNonNull(recommender);
        this.policyRefs = Objects.requireNonNull(policyRefs);
    }

    @Override
    public List<String> suggest(ChatMessage.AnswerBasis answerBasis, List<FaqSearchResponse> searchResults) {
        if (answerBasis != ChatMessage.AnswerBasis.GROUNDED || searchResults == null || searchResults.isEmpty()) {
            return List.of();
        }
        FaqSearchResponse top = searchResults.getFirst();
        try {
            // 검색 결과에는 정책 ID가 없다. 관리자가 바꿀 수 있는 값이라 DB의 현재 값을 쓴다
            String policyRef = top.faqId() == null ? null : policyRefs.find(top.faqId());
            return recommender.recommend(new BaseFaq(top.slotId(), policyRef, top.question()));
        } catch (RuntimeException e) {
            // 추천은 부가 정보라 실패해도 답변은 그대로 저장한다
            log.warn("추천 질문 생성 실패: faqId={}", top.faqId(), e);
            return List.of();
        }
    }

    /** FAQ의 정책 ID. 없으면 null이다. */
    @FunctionalInterface
    public interface PolicyRefFinder {
        String find(long faqId);
    }
}
