package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatMessage.AnswerBasis;
import com.telme.consult.service.PolicyLinkSuggestedQuestions.FaqLookup;
import com.telme.faq.dto.res.FaqSearchResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PolicyLinkSuggestedQuestionsTest {
    // SuggestedQuestionRecommenderTest.sample()의 대표 FAQ가 모두 검색에 나오는 상태
    private static final Map<String, String> ALL_SEARCHABLE =
            Map.of("B-0001", "B 질문", "C-0001", "C 질문", "D-0001", "D 질문");

    private final FaqLookup faqs = mock(FaqLookup.class);
    private final PolicyLinkSuggestedQuestions suggestions =
            new PolicyLinkSuggestedQuestions(SuggestedQuestionRecommenderTest.sample(), faqs);

    @BeforeEach
    void searchableByDefault() {
        when(faqs.searchableQuestions(anyList())).thenReturn(ALL_SEARCHABLE);
    }

    private static FaqSearchResponse faq(Long faqId, String slotId) {
        return new FaqSearchResponse(faqId, slotId, "BILLING", "질문", "답변", 0.9, 1,
                LocalDate.of(2026, 10, 1), 1, "Q_A");
    }

    @Test
    void usesPolicyOfFirstSearchResultOnly() {
        when(faqs.policyRef(1L)).thenReturn("A-01");

        var actual = suggestions.suggest(AnswerBasis.GROUNDED, List.of(faq(1L, "S-1"), faq(2L, "S-2")));

        assertThat(actual).containsExactly("B 질문", "C 질문");
        verify(faqs).policyRef(1L);
        verify(faqs, never()).policyRef(2L);
    }

    @Test
    void appliesRulesOfFirstSearchResult() {
        when(faqs.policyRef(1L)).thenReturn("A-01");

        assertThat(suggestions.suggest(AnswerBasis.GROUNDED, List.of(faq(1L, "TROUBLE-1"))))
                .containsExactly("B 질문");
    }

    // 관리자가 숨긴 대표 FAQ는 버튼을 눌러도 검색되지 않으므로 빼고, 다음 후보로 채운다
    @Test
    void skipsRepresentativeFaqThatSearchCannotFind() {
        when(faqs.policyRef(1L)).thenReturn("A-01");
        var searchable = new HashMap<>(ALL_SEARCHABLE);
        searchable.remove("B-0001");
        when(faqs.searchableQuestions(List.of("B-0001", "C-0001", "D-0001"))).thenReturn(searchable);

        assertThat(suggestions.suggest(AnswerBasis.GROUNDED, List.of(faq(1L, "S-1"))))
                .containsExactly("C 질문", "D 질문");
    }

    // 관리자가 대표 FAQ 질문을 고치면 연결표 JSON이 아니라 현재 질문으로 버튼을 만든다
    @Test
    void usesCurrentQuestionOfRepresentativeFaq() {
        when(faqs.policyRef(1L)).thenReturn("A-01");
        var searchable = new HashMap<>(ALL_SEARCHABLE);
        searchable.put("B-0001", "고친 B 질문");
        when(faqs.searchableQuestions(anyList())).thenReturn(searchable);

        assertThat(suggestions.suggest(AnswerBasis.GROUNDED, List.of(faq(1L, "S-1"))))
                .containsExactly("고친 B 질문", "C 질문");
    }

    @Test
    void returnsEmptyWhenNoRepresentativeFaqIsSearchable() {
        when(faqs.policyRef(1L)).thenReturn("A-01");
        when(faqs.searchableQuestions(anyList())).thenReturn(Map.of());

        assertThat(suggestions.suggest(AnswerBasis.GROUNDED, List.of(faq(1L, "S-1")))).isEmpty();
    }

    // 근거 없음·범위 밖 답변 뒤에 정상 추천을 붙이지 않는다
    @Test
    void returnsEmptyUnlessGrounded() {
        assertThat(suggestions.suggest(AnswerBasis.NO_EVIDENCE, List.of(faq(1L, "S-1")))).isEmpty();
        assertThat(suggestions.suggest(AnswerBasis.OUT_OF_SCOPE, List.of(faq(1L, "S-1")))).isEmpty();
        assertThat(suggestions.suggest(null, List.of(faq(1L, "S-1")))).isEmpty();
        verify(faqs, never()).policyRef(anyLong());
    }

    @Test
    void returnsEmptyWithoutSearchResults() {
        assertThat(suggestions.suggest(AnswerBasis.GROUNDED, List.of())).isEmpty();
        verify(faqs, never()).policyRef(anyLong());
    }

    @Test
    void returnsEmptyWhenFaqHasNoPolicy() {
        when(faqs.policyRef(1L)).thenReturn(null);

        assertThat(suggestions.suggest(AnswerBasis.GROUNDED, List.of(faq(1L, "S-1")))).isEmpty();
        assertThat(suggestions.suggest(AnswerBasis.GROUNDED, List.of(faq(null, "S-1")))).isEmpty();
    }

    // 추천은 부가 정보라 조회가 실패하거나 검색 결과가 이상해도 답변 저장을 막지 않는다
    @Test
    void failureFallsBackToEmpty() {
        when(faqs.policyRef(anyLong())).thenThrow(new IllegalStateException("DB 오류"));

        assertThat(suggestions.suggest(AnswerBasis.GROUNDED, List.of(faq(1L, "S-1")))).isEmpty();
        assertThat(suggestions.suggest(AnswerBasis.GROUNDED, Arrays.asList((FaqSearchResponse) null))).isEmpty();
    }
}
