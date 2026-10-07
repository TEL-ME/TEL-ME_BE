package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatMessage.AnswerBasis;
import com.telme.consult.service.PolicyLinkSuggestedQuestions.PolicyRefFinder;
import com.telme.faq.dto.res.FaqSearchResponse;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

class PolicyLinkSuggestedQuestionsTest {
    private final PolicyRefFinder policyRefs = mock(PolicyRefFinder.class);
    private final PolicyLinkSuggestedQuestions suggestions =
            new PolicyLinkSuggestedQuestions(SuggestedQuestionRecommenderTest.sample(), policyRefs);

    private static FaqSearchResponse faq(Long faqId, String slotId) {
        return new FaqSearchResponse(faqId, slotId, "BILLING", "질문", "답변", 0.9, 1,
                LocalDate.of(2026, 10, 1), 1, "Q_A");
    }

    @Test
    void usesPolicyOfFirstSearchResultOnly() {
        when(policyRefs.find(1L)).thenReturn("A-01");

        var actual = suggestions.suggest(AnswerBasis.GROUNDED, List.of(faq(1L, "S-1"), faq(2L, "S-2")));

        assertThat(actual).containsExactly("B 질문", "C 질문");
        verify(policyRefs).find(1L);
        verify(policyRefs, never()).find(2L);
    }

    @Test
    void appliesRulesOfFirstSearchResult() {
        when(policyRefs.find(1L)).thenReturn("A-01");

        assertThat(suggestions.suggest(AnswerBasis.GROUNDED, List.of(faq(1L, "TROUBLE-1"))))
                .containsExactly("B 질문");
    }

    // 근거 없음·범위 밖 답변 뒤에 정상 추천을 붙이지 않는다
    @Test
    void returnsEmptyUnlessGrounded() {
        assertThat(suggestions.suggest(AnswerBasis.NO_EVIDENCE, List.of(faq(1L, "S-1")))).isEmpty();
        assertThat(suggestions.suggest(AnswerBasis.OUT_OF_SCOPE, List.of(faq(1L, "S-1")))).isEmpty();
        assertThat(suggestions.suggest(null, List.of(faq(1L, "S-1")))).isEmpty();
        verifyNoInteractions(policyRefs);
    }

    @Test
    void returnsEmptyWithoutSearchResults() {
        assertThat(suggestions.suggest(AnswerBasis.GROUNDED, List.of())).isEmpty();
        verifyNoInteractions(policyRefs);
    }

    @Test
    void returnsEmptyWhenFaqHasNoPolicy() {
        when(policyRefs.find(1L)).thenReturn(null);

        assertThat(suggestions.suggest(AnswerBasis.GROUNDED, List.of(faq(1L, "S-1")))).isEmpty();
        assertThat(suggestions.suggest(AnswerBasis.GROUNDED, List.of(faq(null, "S-1")))).isEmpty();
    }

    // 추천은 부가 정보라 조회가 실패해도 답변 저장을 막지 않는다
    @Test
    void lookupFailureFallsBackToEmpty() {
        when(policyRefs.find(anyLong())).thenThrow(new IllegalStateException("DB 오류"));

        assertThat(suggestions.suggest(AnswerBasis.GROUNDED, List.of(faq(1L, "S-1")))).isEmpty();
    }
}
