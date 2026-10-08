package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.exception.FaqAnswerSearchException;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.faq.dto.req.FaqSearchKind;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.rag.converter.AnswerContextConverter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 사용자 원문을 우선 검색하고 근거가 없을 때 정제 질문으로 보완한다. */
public final class FaqSearchAnswerProvider implements AnswerProvider {
    private static final int SEARCH_TOP_K = 3;
    private static final int COMPARISON_CANDIDATE_TOP_K = 10;

    private final FaqSearchService searches;
    private final SearchResultAnswerGenerator answers;
    private final ExecutionTrace trace;
    private final ComparisonEvidenceResolver comparisonEvidence;
    private final FaqCandidateEvidenceResolver candidateEvidence;
    private final AnswerContextConverter sourceConverter;

    public FaqSearchAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers) {
        this(searches, answers, ExecutionTrace.noop());
    }

    public FaqSearchAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers, ExecutionTrace trace) {
        this(searches, answers, trace, ComparisonEvidenceResolver.passthrough());
    }

    public FaqSearchAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers, ExecutionTrace trace,
            ComparisonEvidenceResolver comparisonEvidence) {
        this(searches, answers, trace, comparisonEvidence, FaqCandidateEvidenceResolver.disabled());
    }

    public FaqSearchAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers, ExecutionTrace trace,
            ComparisonEvidenceResolver comparisonEvidence, FaqCandidateEvidenceResolver candidateEvidence) {
        this(searches, answers, trace, comparisonEvidence, candidateEvidence, new AnswerContextConverter());
    }

    public FaqSearchAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers, ExecutionTrace trace,
            ComparisonEvidenceResolver comparisonEvidence, FaqCandidateEvidenceResolver candidateEvidence,
            AnswerContextConverter sourceConverter) {
        this.searches = Objects.requireNonNull(searches);
        this.answers = Objects.requireNonNull(answers);
        this.trace = Objects.requireNonNull(trace);
        this.comparisonEvidence = Objects.requireNonNull(comparisonEvidence);
        this.candidateEvidence = Objects.requireNonNull(candidateEvidence);
        this.sourceConverter = Objects.requireNonNull(sourceConverter);
    }

    @Override
    public GeneratedAnswer generate(AnswerInput input) {
        Objects.requireNonNull(input, "input");
        if (input.purpose() != Purpose.GENERAL_FAQ) {
            throw new IllegalArgumentException("FAQ 답변 경로는 일반 FAQ 상담만 처리할 수 있습니다.");
        }
        if (comparisonEvidence.applies(input.originalUserQuery())) {
            List<FaqSearchResponse> original = search(input, input.originalUserQuery(), FaqSearchKind.ORIGINAL);
            var resolution = comparisonEvidence.resolveDetailed(input.executionId(),
                    input.consultRequestId(), input.originalUserQuery(), original,
                    (query, kind) -> searchComparisonCandidate(input, query, kind));
            if (resolution.answer() != null) {
                trace.stage(input.executionId(), "comparisonAnswer", Map.of(
                        "method", "VERIFIED_FAQ_QUOTE", "faqIds",
                        resolution.sources().stream().map(FaqSearchResponse::faqId).toList()));
                return new GeneratedAnswer(new ChatAnswer(ChatMessage.MessageType.ANSWER,
                        resolution.answer(), ChatMessage.AnswerBasis.GROUNDED, List.of(), null),
                        sourceConverter.toSources(resolution.sources()));
            }
            trace.stage(input.executionId(), "guard", Map.of(
                    "outcome", "NOT_RUN", "reason", "COMPARISON_EVIDENCE_INCOMPLETE"));
            return Objects.requireNonNull(answers.generate(input, List.of()), "generatedAnswer");
        }
        List<FaqSearchResponse> results = searchWithOriginalAndRefinedQuery(input);
        if (results.isEmpty() && !input.streamTokens()) {
            List<FaqSearchResponse> candidates = searchComparisonCandidate(
                    input, input.originalUserQuery(), "FAQ_CANDIDATE");
            FaqSearchResponse verified = candidateEvidence.resolve(
                    input.executionId(), input.consultRequestId(), input.originalUserQuery(), candidates);
            if (verified != null) {
                trace.stage(input.executionId(), "faqCandidateAnswer", Map.of(
                        "method", "VERIFIED_FAQ_ANSWER", "faqId", verified.faqId()));
                return new GeneratedAnswer(new ChatAnswer(ChatMessage.MessageType.ANSWER,
                        verified.answer(), ChatMessage.AnswerBasis.GROUNDED, List.of(), null),
                        sourceConverter.toSources(List.of(verified)));
            }
        }
        if (results.isEmpty()) {
            // 검색 결과가 없으면 RAG 진입 전에 근거 부족 안내를 직접 반환하는 어댑터도 있어 여기서 한 번만 기록한다.
            trace.stage(input.executionId(), "guard",
                    Map.of("outcome", "NOT_RUN", "reason",
                            "NO_SEARCH_RESULTS"));
        }
        return Objects.requireNonNull(
                answers.generate(input, results), "generatedAnswer");
    }

    private List<FaqSearchResponse> searchWithOriginalAndRefinedQuery(AnswerInput input) {
        List<FaqSearchResponse> originalResults = search(input, input.originalUserQuery(), FaqSearchKind.ORIGINAL);
        // 원문 검색에서 후보가 나오면 추가 검색을 생략한다. 후보의 적합성은 여기서 판정하지 않는다.
        if (!originalResults.isEmpty()
                || input.originalUserQuery().equals(input.searchQuery())) {
            return originalResults;
        }
        return search(input, input.searchQuery(), FaqSearchKind.REFINED);
    }

    private List<FaqSearchResponse> search(AnswerInput input, String query, FaqSearchKind kind) {
        return search(input, query, kind.name(), SEARCH_TOP_K, false);
    }

    private List<FaqSearchResponse> searchComparisonCandidate(
            AnswerInput input, String query, String kind) {
        return search(input, query, kind, COMPARISON_CANDIDATE_TOP_K, true);
    }

    private List<FaqSearchResponse> search(
            AnswerInput input, String query, String kind, int topK, boolean unfilteredCandidates) {
        Map<String, Object> request = Map.of("query", query, "kind", kind, "topK", topK,
                "candidateMode", unfilteredCandidates, "consultRequestId", input.consultRequestId());
        trace.append(input.executionId(), "searchRequests", request);
        try {
            FaqSearchRequest searchRequest = unfilteredCandidates ? new FaqSearchRequest(query, topK)
                                                                  : new FaqSearchRequest(query, topK, FaqSearchKind.valueOf(kind));
            var found = unfilteredCandidates
                    ? searches.searchCandidates(searchRequest)
                    : searches.search(searchRequest);
            var results = List.copyOf(found);
            trace.append(input.executionId(), "searchResults", Map.of(
                    "query", query, "kind", kind, "status", results.isEmpty() ? "EMPTY" : "FOUND",
                    "sources", results, "consultRequestId", input.consultRequestId()));
            return results;
        } catch (LlmStreamCancelledException cancelled) {
            trace.append(input.executionId(), "searchResults",
                    Map.of("query", query, "kind", kind, "status", "CANCELLED",
                            "consultRequestId", input.consultRequestId()));
            throw cancelled;
        } catch (RuntimeException failure) {
            trace.append(input.executionId(), "searchResults", Map.of(
                    "query", query, "kind", kind, "status", "ERROR",
                    "errorCode", FaqAnswerSearchException.ERROR_CODE,
                    "consultRequestId", input.consultRequestId()));
            // 빈 목록은 근거 없음이다. 호출·응답 계약 실패는 원인을 보존해 별도로 종료한다.
            throw new FaqAnswerSearchException(failure);
        }
    }

    /** RAG 구현과의 경계다. 검색 결과가 없어도 답변 불가 처리를 위해 호출한다. */
    public interface SearchResultAnswerGenerator {
        GeneratedAnswer generate(AnswerInput input, List<FaqSearchResponse> searchResults);
    }
}
