package com.telme.consult.service;

import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.consult.exception.FaqAnswerSearchException;
import com.telme.llm.exception.LlmStreamCancelledException;

import java.util.List;
import java.util.Objects;

/** 사용자 원문을 우선 검색하고 근거가 없을 때 정제 질문으로 보완한다. */
public final class FaqSearchAnswerProvider implements AnswerProvider {
    private static final int SEARCH_TOP_K = 3;

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
        List<FaqSearchResponse> results = searchWithOriginalAndRefinedQuery(input);
        return Objects.requireNonNull(
                answers.generate(input, results), "generatedAnswer");
    }

    private List<FaqSearchResponse> searchWithOriginalAndRefinedQuery(AnswerInput input) {
        List<FaqSearchResponse> originalResults = search(input.originalUserQuery());
        // 원문 검색에서 후보가 나오면 추가 검색을 생략한다. 후보의 적합성은 여기서 판정하지 않는다.
        if (!originalResults.isEmpty()
                || input.originalUserQuery().equals(input.searchQuery())) {
            return originalResults;
        }
        return search(input.searchQuery());
    }

    private List<FaqSearchResponse> search(String query) {
        try {
            return List.copyOf(searches.search(new FaqSearchRequest(query, SEARCH_TOP_K)));
        } catch (LlmStreamCancelledException cancelled) {
            throw cancelled;
        } catch (RuntimeException failure) {
            // 빈 목록은 근거 없음이다. 호출·응답 계약 실패는 원인을 보존해 별도로 종료한다.
            throw new FaqAnswerSearchException(failure);
        }
    }

    /** RAG 구현과의 경계다. 검색 결과가 없어도 답변 불가 처리를 위해 호출한다. */
    public interface SearchResultAnswerGenerator {
        GeneratedAnswer generate(AnswerInput input, List<FaqSearchResponse> searchResults);
    }
}
