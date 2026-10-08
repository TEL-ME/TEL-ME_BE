package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.consult.service.RagSearchResultAnswerGenerator.SuggestedQuestions;
import com.telme.consult.service.SuggestedQuestionRecommender.BaseFaq;
import com.telme.faq.dto.res.FaqSearchResponse;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 근거가 있는 답변(GROUNDED)에만 LLM에 넘긴 검색 결과 1순위 FAQ의 정책으로 추천 질문을 만든다.
 * 모델이 실제로 참고한 근거는 알 수 없어 1순위를 기준으로 쓰고, 2순위 이하는 보지 않는다.
 */
@Slf4j
public final class PolicyLinkSuggestedQuestions implements SuggestedQuestions {
    private final SuggestedQuestionRecommender recommender;
    private final FaqLookup faqs;
    private final boolean storeQuestionEnabled;

    public PolicyLinkSuggestedQuestions(SuggestedQuestionRecommender recommender, FaqLookup faqs) {
        this(recommender, faqs, false);
    }

    public PolicyLinkSuggestedQuestions(
            SuggestedQuestionRecommender recommender, FaqLookup faqs, boolean storeQuestionEnabled) {
        this.recommender = Objects.requireNonNull(recommender);
        this.faqs = Objects.requireNonNull(faqs);
        this.storeQuestionEnabled = storeQuestionEnabled;
    }

    @Override
    public List<String> suggest(ChatMessage.AnswerBasis answerBasis, List<FaqSearchResponse> searchResults) {
        if (answerBasis != ChatMessage.AnswerBasis.GROUNDED || searchResults == null || searchResults.isEmpty()) {
            return List.of();
        }
        // 추천은 부가 정보라 실패해도 답변은 그대로 저장한다
        try {
            FaqSearchResponse top = searchResults.getFirst();
            List<String> out = new ArrayList<>();
            // 매장 방문이 필요한 답변 뒤에는 매장 찾기를 첫 자리에 둔다. 연결표 추천 자리를 차지하지 않아 최대 3개다
            if (storeQuestionEnabled) {
                String store = recommender.storeQuestion(top.category(), top.answer());
                if (store != null) {
                    out.add(store);
                }
            }
            out.addAll(policyLinkQuestions(top));
            return out.stream().distinct().toList();
        } catch (RuntimeException e) {
            log.warn("추천 질문 생성 실패", e);
            return List.of();
        }
    }

    // 답한 FAQ의 정책 → 그 정책의 대표 질문(현재 질문). 버튼 문장과 같은 기준이라 그대로 비교할 수 있다
    @Override
    public Set<String> questionsAbout(Collection<Long> answeredFaqIds) {
        if (answeredFaqIds == null || answeredFaqIds.isEmpty()) {
            return Set.of();
        }
        try {
            Set<String> policies = answeredFaqIds.stream()
                    .filter(Objects::nonNull)
                    .map(faqs::policyRef)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            List<String> slotIds = recommender.representativeSlotIds(policies);
            if (slotIds.isEmpty()) {
                return Set.of();
            }
            Map<String, String> searchable = faqs.searchableQuestions(slotIds);
            return slotIds.stream()
                    .map(searchable::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException e) {
            log.warn("답한 정책의 추천 질문 조회 실패", e);
            return Set.of();
        }
    }

    private List<String> policyLinkQuestions(FaqSearchResponse top) {
        // 검색 결과에는 정책 ID가 없다. 관리자가 바꿀 수 있는 값이라 DB의 현재 값을 쓴다
        String policyRef = top.faqId() == null ? null : faqs.policyRef(top.faqId());
        List<String> candidates = recommender.candidateSlotIds(new BaseFaq(top.slotId(), policyRef, top.question()));
        if (candidates.isEmpty()) {
            return List.of();
        }
        // 버튼은 질문 문장으로 다시 검색된다. 관리자가 숨기거나 고친 대표 FAQ가 있으므로
        // 검색에 나올 수 있는 FAQ의 현재 질문만 쓰고, 빠진 자리는 다음 후보로 채운다
        Map<String, String> searchable = faqs.searchableQuestions(candidates);
        return candidates.stream()
                .map(searchable::get)
                .filter(Objects::nonNull)
                .distinct()
                .limit(SuggestedQuestionRecommender.MAX_QUESTIONS)
                .toList();
    }

    /** 추천에 필요한 FAQ 조회. */
    public interface FaqLookup {
        /** FAQ의 정책 ID. 없으면 null이다. */
        String policyRef(long faqId);

        /** slotId별 현재 질문. 검색에 나올 수 없는 FAQ(숨김·삭제, 임베딩 미동기화)는 결과에 없다. */
        Map<String, String> searchableQuestions(List<String> slotIds);
    }
}
