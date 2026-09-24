package com.telme.consult.service;

import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;

import java.util.List;
import java.util.Objects;

/** 정제된 상담 질문으로 FAQ를 검색하고 검색 결과를 실제 답변 생성 단계에 넘긴다. */
public final class FaqSearchAnswerProvider implements AnswerProvider {
    private final FaqSearchService searches;
    private final SearchResultAnswerGenerator answers;

    public FaqSearchAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers) {
        this.searches = Objects.requireNonNull(searches);
        this.answers = Objects.requireNonNull(answers);
    }

    @Override
    public GeneratedAnswer generate(AnswerInput input) {
        Objects.requireNonNull(input, "input");
        if (input.purpose() != Purpose.GENERAL_FAQ) {
            throw new IllegalArgumentException("FAQ 답변 경로는 일반 FAQ 상담만 처리할 수 있습니다.");
        }
        List<FaqSearchResponse> results =
                List.copyOf(searches.search(new FaqSearchRequest(input.searchQuery(), null)));
        return Objects.requireNonNull(
                answers.generate(input, results), "generatedAnswer");
    }

    /** RAG 구현과의 경계다. 검색 결과가 없어도 답변 불가 처리를 위해 호출한다. */
    public interface SearchResultAnswerGenerator {
        GeneratedAnswer generate(AnswerInput input, List<FaqSearchResponse> searchResults);
    }
}
