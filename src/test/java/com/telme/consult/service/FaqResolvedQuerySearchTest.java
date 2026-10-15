package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.faq.service.FaqSearchService;
import com.telme.rag.converter.AnswerContextConverter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FaqResolvedQuerySearchTest {
    @Test
    @DisplayName("복원 질문 검색이 실패하면 현재 조건까지 포함해 정제 후보를 검증한다")
    void 복원_본문_전체로_재검색_후보를_검증한다() {
        var searches = mock(FaqSearchService.class);
        var selector = mock(FaqSearchAnswerProvider.RefinedQuerySelector.class);
        String resolved = "[이전 발언] 분실 유심 재발급 [현재 질문] 가입 기간은 18개월입니다. 절차는요?";
        when(searches.search(any())).thenReturn(List.of());
        when(selector.select(resolved, "유심 재발급 절차")).thenReturn(resolved);
        var provider = new FaqSearchAnswerProvider(searches, (input, results) -> null, ExecutionTrace.noop(),
                ComparisonEvidenceResolver.passthrough(), FaqCandidateEvidenceResolver.disabled(),
                new AnswerContextConverter(), null, RagSearchResultAnswerGenerator.SuggestedQuestions.none(), selector);
        var input = new AnswerInput(1, 2, 3, Purpose.GENERAL_FAQ, "같은 유심 절차를 다시 알려주세요.",
                "유심 재발급 절차", Map.of(), resolved, null);

        provider.prepare(input, false);

        verify(selector).select(resolved, "유심 재발급 절차");
        verify(searches).search(any());
        assertThat(input.resolvedUserQuery()).contains("18개월");
    }
}
