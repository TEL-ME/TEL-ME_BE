package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.consult.service.SuggestedQuestionRecommender.FaqRules;
import com.telme.consult.service.SuggestedQuestionRecommender.PolicyLinks;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.req.FaqSearchVector;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

class FaqCandidateNoAnswerSuggestionsTest {
    private final FaqSearchService searches = mock(FaqSearchService.class);
    private final SuggestedQuestionRecommender recommender = new SuggestedQuestionRecommender(
            new PolicyLinks(Map.of(), Map.of()), new FaqRules(null, null, null, Set.of("DEVICE-0031")));
    private final FaqCandidateNoAnswerSuggestions suggestions =
            new FaqCandidateNoAnswerSuggestions(searches, recommender);

    private static FaqSearchResponse candidate(String slotId, String question, double score) {
        return new FaqSearchResponse(1L, slotId, "DEVICE", question, "답변", score, 1, null, 1, "Q_A");
    }

    // 측정한 기준과 같게 질문+답변 벡터로 찾고, 하한 이상인 1위 하나만 쓴다
    @Test
    void usesFirstQaCandidateAboveMinScore() {
        when(searches.searchCandidates(any())).thenReturn(List.of(
                candidate("DEVICE-0051", "폰 잃어버리면 정지부터 해야 해요?", 0.66),
                candidate("DEVICE-0099", "분실 신고는 어떻게 해요?", 0.63)));

        assertThat(suggestions.suggest("지하철에서 폰을 놓고 내렸어요")).containsExactly("폰 잃어버리면 정지부터 해야 해요?");
        verify(searches).searchCandidates(new FaqSearchRequest("지하철에서 폰을 놓고 내렸어요", 10, FaqSearchVector.QA));
    }

    @Test
    void returnsEmptyBelowMinScore() {
        when(searches.searchCandidates(any())).thenReturn(List.of(candidate("DEVICE-0051", "질문", 0.599)));

        assertThat(suggestions.suggest("내일 서울 날씨 알려줘")).isEmpty();
    }

    // 질문을 그대로 보내도 답이 안 나오는 FAQ는 버튼을 눌러도 답이 없으므로 다음 후보를 쓴다
    @Test
    void skipsUnanswerableFaqAndTakesNextCandidate() {
        when(searches.searchCandidates(any())).thenReturn(List.of(
                candidate("DEVICE-0031", "폰 잃어버렸을 때 뭐부터 해야 해요?", 0.70),
                candidate("DEVICE-0051", "폰 잃어버리면 정지부터 해야 해요?", 0.64),
                candidate("DEVICE-0099", "분실 신고는 어떻게 해요?", 0.58)));

        assertThat(suggestions.suggest("폰을 잃어버렸어요")).containsExactly("폰 잃어버리면 정지부터 해야 해요?");
    }

    // 답을 못 할 때 버튼은 측정한 규칙 그대로 문제 상황 FAQ도 후보로 쓴다(건너뛰는 것은 복합 질문 버튼만)
    @Test
    void noAnswerButtonKeepsTroubleFaq() {
        var withTrouble = new FaqCandidateNoAnswerSuggestions(searches, new SuggestedQuestionRecommender(
                new PolicyLinks(Map.of(), Map.of()), new FaqRules(Set.of("DEVICE-0051"), null, null, Set.of())));
        when(searches.searchCandidates(any())).thenReturn(List.of(
                candidate("DEVICE-0051", "폰 잃어버리면 정지부터 해야 해요?", 0.66),
                candidate("DEVICE-0099", "분실 신고는 어떻게 해요?", 0.63)));

        assertThat(withTrouble.suggest("폰을 잃어버렸어요")).containsExactly("폰 잃어버리면 정지부터 해야 해요?");
        assertThat(withTrouble.firstCandidate("폰을 잃어버렸어요", true)).contains("분실 신고는 어떻게 해요?");
    }

    // 관리자가 등록한 FAQ(slotId 없음)는 측정하지 않아 후보로 쓴다
    @Test
    void adminFaqWithoutSlotIdIsCandidate() {
        when(searches.searchCandidates(any())).thenReturn(List.of(candidate(null, "관리자 등록 질문", 0.65)));

        assertThat(suggestions.suggest("질문")).containsExactly("관리자 등록 질문");
    }

    @Test
    void blankQuestionSkipsSearch() {
        assertThat(suggestions.suggest(" ")).isEmpty();
        assertThat(suggestions.suggest(null)).isEmpty();
        verifyNoInteractions(searches);
    }

    // 버튼은 부가 정보라 검색이 실패해도 답변 저장을 막지 않는다
    @Test
    void searchFailureFallsBackToEmpty() {
        when(searches.searchCandidates(any())).thenThrow(new IllegalStateException("임베딩 서버 오류"));

        assertThat(suggestions.suggest("질문")).isEmpty();
    }
}
