package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ConsultationQuestion;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.dto.ClarificationPlan;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.exception.FaqAnswerSearchException;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.service.ConsultChatProcessingService.Prepared;
import com.telme.consult.service.RagSearchResultAnswerGenerator.SuggestedQuestions;
import com.telme.faq.dto.req.FaqSearchKind;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.rag.converter.AnswerContextConverter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.Set;

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
    private final FaqClarificationPlanner planner;
    private final SuggestedQuestions suggestedQuestions;
    private final RefinedQuerySelector refinedQueries;
    // null이면 회색 구간 판정을 쓰지 않는 기존 검색이다
    private final FaqGrayZoneSelector grayZone;
    // 문맥을 풀어 쓴 질문이 요금제를 가리키는지. 원문 재검색은 이때만 한다
    private final Predicate<String> aboutPlan;

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
        this(searches, answers, trace, comparisonEvidence, candidateEvidence, sourceConverter,
                null, SuggestedQuestions.none());
    }

    public FaqSearchAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers, ExecutionTrace trace,
            ComparisonEvidenceResolver comparisonEvidence, FaqCandidateEvidenceResolver candidateEvidence,
            AnswerContextConverter sourceConverter, FaqClarificationPlanner planner) {
        this(searches, answers, trace, comparisonEvidence, candidateEvidence, sourceConverter, planner,
                SuggestedQuestions.none());
    }

    public FaqSearchAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers, ExecutionTrace trace,
            ComparisonEvidenceResolver comparisonEvidence, FaqCandidateEvidenceResolver candidateEvidence,
            AnswerContextConverter sourceConverter, SuggestedQuestions suggestedQuestions) {
        this(searches, answers, trace, comparisonEvidence, candidateEvidence, sourceConverter, null,
                suggestedQuestions);
    }

    public FaqSearchAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers, ExecutionTrace trace,
            ComparisonEvidenceResolver comparisonEvidence, FaqCandidateEvidenceResolver candidateEvidence,
            AnswerContextConverter sourceConverter, FaqClarificationPlanner planner,
            SuggestedQuestions suggestedQuestions) {
        this(searches, answers, trace, comparisonEvidence, candidateEvidence, sourceConverter, planner,
                suggestedQuestions, RefinedQuerySelector.unchecked());
    }

    public FaqSearchAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers, ExecutionTrace trace,
            ComparisonEvidenceResolver comparisonEvidence, FaqCandidateEvidenceResolver candidateEvidence,
            AnswerContextConverter sourceConverter, FaqClarificationPlanner planner,
            SuggestedQuestions suggestedQuestions, RefinedQuerySelector refinedQueries) {
        this(searches, answers, trace, comparisonEvidence, candidateEvidence, sourceConverter, planner,
                suggestedQuestions, refinedQueries, null);
    }

    public FaqSearchAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers, ExecutionTrace trace,
            ComparisonEvidenceResolver comparisonEvidence, FaqCandidateEvidenceResolver candidateEvidence,
            AnswerContextConverter sourceConverter, FaqClarificationPlanner planner,
            SuggestedQuestions suggestedQuestions, RefinedQuerySelector refinedQueries,
            FaqGrayZoneSelector grayZone) {
        this(searches, answers, trace, comparisonEvidence, candidateEvidence, sourceConverter, planner,
                suggestedQuestions, refinedQueries, grayZone, question -> false);
    }

    public FaqSearchAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers, ExecutionTrace trace,
            ComparisonEvidenceResolver comparisonEvidence, FaqCandidateEvidenceResolver candidateEvidence,
            AnswerContextConverter sourceConverter, FaqClarificationPlanner planner,
            SuggestedQuestions suggestedQuestions, RefinedQuerySelector refinedQueries,
            FaqGrayZoneSelector grayZone, Predicate<String> aboutPlan) {
        this.searches = Objects.requireNonNull(searches);
        this.answers = Objects.requireNonNull(answers);
        this.trace = Objects.requireNonNull(trace);
        this.comparisonEvidence = Objects.requireNonNull(comparisonEvidence);
        this.candidateEvidence = Objects.requireNonNull(candidateEvidence);
        this.sourceConverter = Objects.requireNonNull(sourceConverter);
        this.planner = planner;
        this.suggestedQuestions = Objects.requireNonNull(suggestedQuestions);
        this.refinedQueries = Objects.requireNonNull(refinedQueries);
        this.grayZone = grayZone;
        this.aboutPlan = Objects.requireNonNull(aboutPlan);
    }

    @Override
    public GeneratedAnswer generate(AnswerInput input) {
        // 한 번에 처리하는 경로는 되묻기 계획을 쓰지 않는다
        return answer(input, prepare(input, false));
    }

    /** 검색과 되묻기 판단만 한다. 답변은 만들지 않아 되묻는 경우 답변 메시지를 먼저 열지 않는다. */
    @Override
    public boolean clarifies() {
        return planner != null;
    }

    @Override
    public Prepared prepare(AnswerInput input, boolean plansClarification) {
        Objects.requireNonNull(input, "input");
        if (input.purpose() != Purpose.GENERAL_FAQ) {
            throw new IllegalArgumentException("FAQ 답변 경로는 일반 FAQ 상담만 처리할 수 있습니다.");
        }
        if (comparisonEvidence.applies(ConsultationQuestion.extract(input.originalUserQuery()))) {
            List<FaqSearchResponse> original = search(input, input.originalUserQuery(), FaqSearchKind.ORIGINAL);
            var resolution = resolveComparison(input, original);
            boolean retried = false;
            // 비교 답변은 원문 검색 결과에서 그대로 인용할 FAQ를 고른다. 회색 구간 판정으로 결과가 바뀌면 인용하던 FAQ가
            // 빠지거나 뒤로 밀려 답하지 못할 수 있다(docs/GRAY_ZONE_UPPER_BAND.md 5.7절). 그때만 판정을 붙이기 전
            // 검색 결과로 한 번 더 고른다. 인용문은 비교 답변기가 다시 확인하므로 판정이 뺀 FAQ가 들어와도 그대로 답하지 않는다
            if (resolution.answer() == null && grayZone != null) {
                List<FaqSearchResponse> beforeGrayZone = search(input, input.originalUserQuery(),
                        FaqSearchKind.ORIGINAL.name(), SEARCH_TOP_K, false, false);
                if (!faqIds(beforeGrayZone).equals(faqIds(original))) {
                    retried = true;
                    resolution = resolveComparison(input, beforeGrayZone);
                }
            }
            if (retried) {
                trace.stage(input.executionId(), "comparisonRetry", Map.of(
                        "reason", "GRAY_ZONE_CHANGED_ORIGINAL_RESULTS", "answered", resolution.answer() != null));
            }
            if (resolution.answer() != null) {
                trace.stage(input.executionId(), "comparisonAnswer", Map.of(
                        "method", "VERIFIED_FAQ_QUOTE", "faqIds", faqIds(resolution.sources())));
                // 비교한 두 FAQ가 모두 근거라 FAQ마다 추천 질문을 하나씩 붙인다. 비교한 정책 자체를 가리키는 추천은 뺀다
                Set<String> answered = Objects.requireNonNull(suggestedQuestions.questionsAbout(
                        resolution.sources().stream().map(FaqSearchResponse::faqId).toList()), "answeredQuestions");
                List<String> followUps = SuggestedQuestions.oneFromEach(resolution.sources().stream()
                        .map(source -> Objects.requireNonNull(suggestedQuestions.suggest(
                                ChatMessage.AnswerBasis.GROUNDED, List.of(source)), "suggestedQuestions").stream()
                                .filter(question -> !answered.contains(question))
                                .toList())
                        .toList());
                return Prepared.answered(new GeneratedAnswer(
                        new ChatAnswer(ChatMessage.MessageType.ANSWER, resolution.answer(),
                                ChatMessage.AnswerBasis.GROUNDED, followUps, null),
                        sourceConverter.toSources(resolution.sources())));
            }
            trace.stage(input.executionId(), "guard", Map.of(
                    "outcome", "NOT_RUN", "reason", "COMPARISON_EVIDENCE_INCOMPLETE"));
            return Prepared.answered(
                    Objects.requireNonNull(answers.generate(input, List.of()), "generatedAnswer"));
        }
        List<FaqSearchResponse> results = searchWithOriginalAndRefinedQuery(input);
        if (results.isEmpty() && !input.streamTokens()) {
            List<FaqSearchResponse> candidates = searchComparisonCandidate(
                    input, input.originalUserQuery(), "FAQ_CANDIDATE");
            String evidenceQuestion = ConsultationQuestion.extract(input.candidateEvidenceQuery());
            // 복합 질문은 검색용 문장이 공백 없이 정규화된다. 후보 FAQ를 선택한 모델이
            // 인용문만 형식에 맞추지 못한 경우까지 근거 부족으로 버리지는 않는다.
            boolean allowQuoteRepairFailure =
                    !evidenceQuestion.equals(ConsultationQuestion.extract(input.originalUserQuery()));
            // 근거 검증에 넣은 문장을 남겨, 같은 입력(후보는 searchResults의 FAQ_CANDIDATE)으로 모델을 다시 비교할 수 있게 한다
            Map<String, Object> evidenceInput = new LinkedHashMap<>();
            evidenceInput.put("consultRequestId", input.consultRequestId());
            evidenceInput.put("question", evidenceQuestion);
            evidenceInput.put("allowQuoteRepairFailure", allowQuoteRepairFailure);
            String customerMessage = input.customerMessage() == null
                    ? null : ConsultationQuestion.extract(input.customerMessage());
            evidenceInput.put("customerMessage", customerMessage);
            trace.append(input.executionId(), "faqCandidateEvidenceInputs", evidenceInput);
            FaqSearchResponse verified = candidateEvidence.resolve(
                    input.executionId(), input.consultRequestId(), evidenceQuestion, customerMessage, candidates,
                    allowQuoteRepairFailure);
            if (verified != null) {
                trace.stage(input.executionId(), "faqCandidateAnswer", Map.of(
                        "method", "VERIFIED_FAQ_ANSWER", "faqId", verified.faqId()));
                // 확인된 후보 하나가 답의 근거라 일반 답변의 검색 결과 1순위처럼 추천 질문을 만든다
                List<String> followUps = Objects.requireNonNull(suggestedQuestions.suggest(
                        ChatMessage.AnswerBasis.GROUNDED, List.of(verified)), "suggestedQuestions");
                return Prepared.answered(new GeneratedAnswer(
                        new ChatAnswer(ChatMessage.MessageType.ANSWER, verified.answer(),
                                ChatMessage.AnswerBasis.GROUNDED, followUps, null),
                        sourceConverter.toSources(List.of(verified))));
            }
        }
        if (results.isEmpty()) {
            // 검색 결과가 없으면 RAG 진입 전에 근거 부족 안내를 직접 반환하는 어댑터도 있어 여기서 한 번만 기록한다.
            trace.stage(input.executionId(), "guard",
                    Map.of("outcome", "NOT_RUN", "reason", "NO_SEARCH_RESULTS"));
            return new Prepared(results, ClarificationPlan.none());
        }
        ClarificationPlan plan = planner == null || !plansClarification
                ? ClarificationPlan.none()
                : planner.plan(input.executionId(), ConsultationQuestion.extract(input.originalUserQuery()), results);
        return new Prepared(results, plan);
    }

    /** 검색 결과를 다시 쓰므로 답변을 만들 때 검색을 또 하지 않는다. */
    @Override
    public GeneratedAnswer generate(AnswerInput input, Prepared prepared) {
        return answer(input, Objects.requireNonNull(prepared, "prepared"));
    }

    private GeneratedAnswer answer(AnswerInput input, Prepared prepared) {
        // 비교·후보 근거 검증은 준비 단계에서 답을 확정한다. 그때는 모델을 다시 부르지 않는다
        return prepared.answer() != null
                ? prepared.answer()
                : Objects.requireNonNull(answers.generate(input, prepared.searchResults()), "generatedAnswer");
    }


    private List<FaqSearchResponse> searchWithOriginalAndRefinedQuery(AnswerInput input) {
        String original = ConsultationQuestion.extract(input.originalUserQuery());
        String resolved = ConsultationQuestion.extract(input.resolvedUserQuery());
        String refined = ConsultationQuestion.extract(input.searchQuery());
        // 호칭 제거는 문맥 복원과 구분한다. 같은 상담 본문이면 임베딩을 한 번만 호출한다.
        if (!original.equals(resolved)) {
            List<FaqSearchResponse> results = search(input, resolved, FaqSearchKind.RESOLVED);
            if (results.isEmpty() && !resolved.equals(refined)) {
                results = search(input, refined, FaqSearchKind.REFINED);
            }
            // 문맥을 풀어 쓴 검색어는 "5G 스탠다드 해지 위약금"처럼 명사를 늘어놓아 FAQ 질문과 덜 비슷하고, 앞 대화의 상품 이름이
            // 그 상품 FAQ 쪽으로 끌어 하한 아래로 떨어진다. 그래도 못 찾으면 고객 원문("그거 해지하면 위약금은?")으로 한 번 더 찾는다.
            // 원문에는 대상이 빠져 있어 대상이 요금제일 때만 찾는다. 요금제의 해지·위약금·명의변경 같은 규정은 요금제와 관계없이 같지만,
            // 부가서비스("컬러링", "데이터 쉐어링")를 물었을 때 원문으로 찾으면 회선 해지·가입 FAQ로 잘못 답했고 판정기가 막지 못했다.
            // 후보는 점수와 관계없이 모두 판정기가 문맥을 풀어 쓴 질문으로 판정한다. 판정기가 꺼져 있으면 찾지 않는다
            return results.isEmpty() && grayZone != null && aboutPlan.test(resolved)
                    ? searchOriginalForResolved(input, original) : results;
        }
        List<FaqSearchResponse> results = search(input, original, FaqSearchKind.ORIGINAL);
        if (!results.isEmpty() || original.equals(refined)) {
            return results;
        }
        // develop의 의미 검증을 유지하되, 호칭만 다른 문장은 같은 상담 본문으로 비교한다.
        String selected = ConsultationQuestion.extract(Objects.requireNonNull(
                refinedQueries.select(original, refined, input.executionId()), "refinedQuery"));
        trace.append(input.executionId(), "refinedQueryChecks", Map.of(
                "candidate", refined, "selected", selected,
                "searched", !selected.equals(original),
                "consultRequestId", input.consultRequestId()));
        return selected.equals(original) ? results : search(input, selected, FaqSearchKind.REFINED);
    }

    private List<FaqSearchResponse> search(AnswerInput input, String query, FaqSearchKind kind) {
        return search(input, query, kind.name(), SEARCH_TOP_K, false, true);
    }

    private List<FaqSearchResponse> searchOriginalForResolved(AnswerInput input, String original) {
        return search(input, original, FaqSearchKind.ORIGINAL.name(), SEARCH_TOP_K, false, true, true);
    }

    private List<FaqSearchResponse> searchComparisonCandidate(
            AnswerInput input, String query, String kind) {
        return search(input, query, kind, COMPARISON_CANDIDATE_TOP_K, true, true);
    }

    private ComparisonEvidenceResolver.Resolution resolveComparison(
            AnswerInput input, List<FaqSearchResponse> original) {
        String question = ConsultationQuestion.extract(input.originalUserQuery());
        // 비교 근거 검증에 넣은 문장과 원문 검색 결과의 FAQ 순서를 남겨, 같은 입력으로 모델을 다시 비교할 수 있게 한다.
        // 검색 결과 본문은 같은 상담의 searchResults에 이미 있어 FAQ id만 남긴다
        Map<String, Object> evidenceInput = new LinkedHashMap<>();
        evidenceInput.put("consultRequestId", input.consultRequestId());
        evidenceInput.put("question", question);
        evidenceInput.put("originalFaqIds", original.stream().map(FaqSearchResponse::faqId).toList());
        trace.append(input.executionId(), "comparisonEvidenceInputs", evidenceInput);
        return comparisonEvidence.resolveDetailed(input.executionId(),
                input.consultRequestId(), question, original,
                (query, kind) -> searchComparisonCandidate(input, query, kind));
    }

    private static List<Long> faqIds(List<FaqSearchResponse> results) {
        return results.stream().map(FaqSearchResponse::faqId).toList();
    }

    // useGrayZone이 false면 회색 구간이 켜져 있어도 판정을 붙이기 전 임계값으로 검색한다
    private List<FaqSearchResponse> search(AnswerInput input, String query, String kind, int topK,
            boolean unfilteredCandidates, boolean useGrayZone) {
        return search(input, query, kind, topK, unfilteredCandidates, useGrayZone, false);
    }

    // judgeAll이면 통과 선 이상의 후보도 판정기에 넘긴다(재검색, 문맥 질문의 원문 검색)
    private List<FaqSearchResponse> search(AnswerInput input, String query, String kind, int topK,
            boolean unfilteredCandidates, boolean useGrayZone, boolean judgeAll) {
        query = ConsultationQuestion.extract(query);
        boolean grayZoneSearch = !unfilteredCandidates && grayZone != null && useGrayZone;
        Map<String, Object> request = new java.util.LinkedHashMap<>(Map.of("query", query, "kind", kind, "topK", topK,
                "candidateMode", unfilteredCandidates, "grayZone", grayZoneSearch,
                "consultRequestId", input.consultRequestId()));
        if (judgeAll) {
            request.put("judgeAll", true);
        }
        trace.append(input.executionId(), "searchRequests", request);
        try {
            FaqSearchRequest searchRequest = unfilteredCandidates ? new FaqSearchRequest(query, topK)
                                                                  : new FaqSearchRequest(query, topK, FaqSearchKind.valueOf(kind));
            var found = unfilteredCandidates ? searches.searchCandidates(searchRequest)
                    : grayZoneSearch ? searchGrayZone(input, searchRequest, judgeAll)
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

    // 판정은 검색 문장이 아니라 고객이 실제로 물은 질문으로 한다. 원문·문맥 복원·재검색 어느 경로든 같다
    // 어떤 후보를 왜 남기고 뺐는지(판정 결과·위쪽 구간 여부·지연)를 검색마다 실행 기록에 남긴다. 모델이 쓴 문장은 넣지 않는다
    private List<FaqSearchResponse> searchGrayZone(AnswerInput input, FaqSearchRequest searchRequest, boolean judgeAll) {
        var candidates = searches.searchGrayZone(searchRequest);
        // 재검색 문장은 FAQ 말투로 다듬어져 점수가 높게 나온다. 원문 검색에서 판정기가 막은 FAQ가 통과 선을 넘어
        // 판정 없이 들어오지 않게 재검색 후보는 모두 판정한다. 원문에서 받은 판정은 같은 실행 안에서 다시 쓴다.
        // 문맥 질문을 원문으로 다시 찾을 때도(judgeAll) 원문에 대상이 빠져 있어 모두 판정한다
        if (searchRequest.kind() == FaqSearchKind.REFINED || judgeAll) {
            candidates = candidates.judgingAll();
        }
        long started = System.nanoTime();
        var selection = grayZone.select(input.executionId(), input.consultRequestId(), input.resolvedUserQuery(),
                candidates, searchRequest.topK());
        trace.append(input.executionId(), "grayZoneJudgments", Map.of(
                "query", searchRequest.query(), "kind", String.valueOf(searchRequest.kind()),
                "judgments", selection.judgments(),
                "elapsedMs", (System.nanoTime() - started) / 1_000_000,
                "consultRequestId", input.consultRequestId()));
        return selection.results();
    }

    /** 원문 검색이 0건일 때 재검색에 쓸 문장을 고른다. 원문을 돌려주면 재검색하지 않는다. */
    public interface RefinedQuerySelector {
        String select(String originalUserQuery, String searchQuery, Long executionId);

        /** 검색 문장을 검증 없이 쓴다. 이미 검증된 문장만 넘기는 경로와 테스트에서 쓴다. */
        static RefinedQuerySelector unchecked() {
            return (originalUserQuery, searchQuery, executionId) -> searchQuery;
        }
    }

    /** RAG 구현과의 경계다. 검색 결과가 없어도 답변 불가 처리를 위해 호출한다. */
    public interface SearchResultAnswerGenerator {
        GeneratedAnswer generate(AnswerInput input, List<FaqSearchResponse> searchResults);
    }
}
