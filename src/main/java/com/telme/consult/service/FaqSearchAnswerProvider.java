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
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.service.ConsultChatProcessingService.PreparedAnswer;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.PlanChangeConditions;
import com.telme.consult.converter.ConfirmedConditionConverter;
import java.util.HashMap;
import java.util.Map;

import java.util.List;
import java.util.Objects;

/** 사용자 원문을 우선 검색하고 근거가 없을 때 정제 질문으로 보완한다. */
public final class FaqSearchAnswerProvider implements AnswerProvider {
    private static final int SEARCH_TOP_K = 3;

    private final FaqSearchService searches;
    private final SearchResultAnswerGenerator answers;
    private final ExecutionTrace trace;
    private final DialogueService dialogue;

    public FaqSearchAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers) {
        this(searches, answers, ExecutionTrace.noop());
    }

    public FaqSearchAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers, ExecutionTrace trace) {
        this(searches, answers, trace, null);
    }

    public FaqSearchAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers, ExecutionTrace trace,
            DialogueService dialogue) {
        this.searches = Objects.requireNonNull(searches);
        this.answers = Objects.requireNonNull(answers);
        this.trace = Objects.requireNonNull(trace);
        this.dialogue = dialogue;
    }

    @Override
    public PreparedAnswer prepare(AnswerInput input, Map<String, Condition> conditions) {
        if (input.purpose() != Purpose.GENERAL_FAQ) throw new IllegalArgumentException("FAQ 상담이 필요합니다.");
        boolean personal = PlanChangeConditions.isPersonalQuestion(input.originalUserQuery());
        boolean criteria = PlanChangeConditions.isCriteriaQuestion(input.originalUserQuery());
        if (dialogue == null || !personal && !criteria) return AnswerProvider.super.prepare(input, conditions);
        List<FaqSearchResponse> results = searchWithOriginalAndRefinedQuery(input);
        if (!results.isEmpty() && !PlanChangeClarificationPolicy.coversRequiredPolicy(input.originalUserQuery(), conditions, results)) {
            // 가입월 제한이 원문 Top-3에 빠지는 사례를 보완한다. 벡터·임계값·Top-K는 그대로다.
            results = search(input, PlanChangeClarificationPolicy.POLICY_QUERY, "CLARIFICATION_POLICY");
        }
        if (!PlanChangeClarificationPolicy.coversRequiredPolicy(input.originalUserQuery(), conditions, results)) {
            // 사용자 조건을 물어도 정책 근거가 채워지지 않는다. 개인 가능 여부를 추측하지 않는다.
            results = List.of();
        }
        var decision = dialogue.assessFaq(input.consultRequestId(),
                input.originalUserQuery(), conditions, results);
        AnswerInput generationInput = input;
        if (decision != null) {
            var confirmed = new HashMap<>(new ConfirmedConditionConverter().convert(decision.conditions()));
            confirmed.put(com.telme.rag.service.AnswerPromptTemplates.PLAN_CHANGE_GUIDANCE_KEY,
                    PlanChangeClarificationPolicy.generationGuidance(decision.conditions(), results));
            String policyAnswer = PlanChangeClarificationPolicy.policyCompletion(decision.conditions(), results);
            if (policyAnswer != null) confirmed.put(com.telme.rag.service.AnswerPromptTemplates.PLAN_CHANGE_POLICY_ANSWER_KEY, policyAnswer);
            generationInput = new AnswerInput(input.executionId(), input.sessionId(), input.consultRequestId(),
                    input.purpose(), input.originalUserQuery(), input.searchQuery(), confirmed);
        }
        if (decision == null && criteria) {
            String criteriaAnswer = PlanChangeClarificationPolicy.criteriaCompletion(results);
            if (criteriaAnswer != null) {
                var confirmed = new HashMap<>(input.confirmedConditions());
                confirmed.put(com.telme.rag.service.AnswerPromptTemplates.PLAN_CHANGE_POLICY_ANSWER_KEY, criteriaAnswer);
                generationInput = new AnswerInput(input.executionId(), input.sessionId(), input.consultRequestId(),
                        input.purpose(), input.originalUserQuery(), input.searchQuery(), confirmed);
            }
        }
        AnswerInput finalInput = generationInput;
        List<FaqSearchResponse> finalResults = results;
        if (results.isEmpty()) trace.stage(input.executionId(), "guard", Map.of("outcome", "NOT_RUN", "reason", "NO_POLICY_OR_SEARCH_EVIDENCE"));
        return new PreparedAnswer(decision, () -> answers.generate(finalInput, finalResults));
    }

    @Override
    public GeneratedAnswer generate(AnswerInput input) {
        Objects.requireNonNull(input, "input");
        if (input.purpose() != Purpose.GENERAL_FAQ) {
            throw new IllegalArgumentException("FAQ 답변 경로는 일반 FAQ 상담만 처리할 수 있습니다.");
        }
        List<FaqSearchResponse> results = searchWithOriginalAndRefinedQuery(input);
        if (results.isEmpty()) {
            // 검색 결과가 없으면 RAG 진입 전에 근거 부족 안내를 직접 반환하는 어댑터도 있어 여기서 한 번만 기록한다.
            trace.stage(input.executionId(), "guard",
                    Map.of("outcome", "NOT_RUN", "reason", "NO_SEARCH_RESULTS"));
        }
        return Objects.requireNonNull(
                answers.generate(input, results), "generatedAnswer");
    }

    private List<FaqSearchResponse> searchWithOriginalAndRefinedQuery(AnswerInput input) {
        List<FaqSearchResponse> originalResults = search(input, input.originalUserQuery(), "ORIGINAL");
        // 원문 검색에서 후보가 나오면 추가 검색을 생략한다. 후보의 적합성은 여기서 판정하지 않는다.
        if (!originalResults.isEmpty()
                || input.originalUserQuery().equals(input.searchQuery())) {
            return originalResults;
        }
        return search(input, input.searchQuery(), "REFINED");
    }

    private List<FaqSearchResponse> search(AnswerInput input, String query, String kind) {
        Map<String, Object> request = Map.of("query", query, "kind", kind, "topK", SEARCH_TOP_K,
                "consultRequestId", input.consultRequestId());
        trace.append(input.executionId(), "searchRequests", request);
        try {
            var results = List.copyOf(searches.search(new FaqSearchRequest(query, SEARCH_TOP_K)));
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
