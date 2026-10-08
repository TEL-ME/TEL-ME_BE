package com.telme.probe;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.service.ConsultChatEvents;
import com.telme.consult.service.ConsultChatPersistenceService;
import com.telme.consult.service.ConsultChatProcessingService;
import com.telme.consult.service.ConsultService;
import com.telme.consult.service.ComparisonEvidenceResolver;
import com.telme.consult.service.FaqCandidateEvidenceResolver;
import com.telme.consult.service.FaqSearchAnswerProvider;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.entity.Faq;
import com.telme.faq.repository.FaqRepository;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.service.AnswerGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// 사용 중인 FAQ를 고정해 실제 RAG 답변 생성과 복합 답변 결합을 확인한다.
@SpringBootTest(properties = {
        "llm.provider=ollama", "telme.consult.chat-integration-enabled=false",
        "rag.evidence-check.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "TELME_FORCED_MULTI_FAQ_PROBE", matches = "true")
class ForcedEvidenceMultiFaqProbe {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final long SESSION_ID = 777L;
    private static final LlmStreamHandler SILENT = new LlmStreamHandler() {
        public void onToken(String token) {}
        public void onComplete() {}
        public void onError(Throwable error) {}
    };
    private static final List<Case> CASES = List.of(
            new Case("MULTI-001", "요금제 종류와 유심 재발급 비용 알려줘",
                    List.of(new Part("5G와 LTE 요금제는 각각 몇 종류인가요?", List.of(117L)),
                            new Part("물리 유심 재발급 비용은 얼마인가요?", List.of(297L)))),
            new Case("MULTI-002", "명의 변경 서류와 번호 이동 서류 알려줘",
                    List.of(new Part("명의 변경에 필요한 서류는 무엇인가요?", List.of(65L)),
                            new Part("번호 이동에 필요한 서류는 무엇인가요?", List.of(125L)))),
            new Case("MULTI-003", "명의 변경 서류와 없는 혜택 알려줘",
                    List.of(new Part("명의 변경에 필요한 서류는 무엇인가요?", List.of(65L)),
                            new Part("명의 변경 시 무료 해외 항공권을 주나요?", List.of()))),
            new Case("MULTI-004", "요금제 종류, 유심 재발급 비용, 청소년 가입 연령 알려줘",
                    List.of(new Part("5G와 LTE 요금제는 각각 몇 종류인가요?", List.of(117L)),
                            new Part("물리 유심 재발급 비용은 얼마인가요?", List.of(297L)),
                            new Part("청소년 요금제 가입 연령은 몇 세인가요?", List.of(121L)))));

    @Autowired private FaqRepository faqs;
    @Autowired private FaqSearchService searches;
    @Autowired private FaqCandidateEvidenceResolver candidateResolver;
    @Autowired private ComparisonEvidenceResolver comparisonResolver;
    @Autowired private AnswerGenerator generator;

    @Test
    void evaluateIndependentFaqQuestionsWithFixedEvidence() throws Exception {
        evaluate(false);
    }

    @Test
    void evaluateIndependentFaqQuestionsWithActualSearch() throws Exception {
        evaluate(true);
    }

    private void evaluate(boolean actualSearch) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Case testCase : CASES) {
            String selectedCase = System.getenv("TELME_MULTI_FAQ_CASE");
            if (selectedCase != null && !selectedCase.equals(testCase.id())) {
                continue;
            }
            var persistence = mock(ConsultChatPersistenceService.class);
            var events = mock(ConsultChatEvents.class);
            var sent = new ArrayList<String>();
            var stream = new LlmStreamHandler() {
                public void onToken(String token) { sent.add(token); }
                public void onComplete() {}
                public void onError(Throwable error) {}
            };
            long executionId = 9000L + rows.size();
            when(persistence.startAnswer(executionId, SESSION_ID)).thenReturn(
                    new ChatExecutionState(SESSION_ID, executionId, ChatExecution.Status.RUNNING, null, null));
            when(persistence.persistFinalAnswers(eq(executionId), eq(SESSION_ID), anyList(), any(), anyList()))
                    .thenReturn(new ChatExecutionState(SESSION_ID, executionId,
                            ChatExecution.Status.COMPLETED, null, null));
            when(events.stream(executionId)).thenReturn(stream);

            var calls = new ArrayList<Map<String, Object>>();
            var processor = new ConsultChatProcessingService(
                    ignored -> ConsultChatProcessingService.AnalyzedTurn.compound(
                            buildTurns(testCase.parts())),
                    input -> {
                        int index = calls.size();
                        Part part = testCase.parts().get(index);
                        long started = System.nanoTime();
                        ConsultChatProcessingService.GeneratedAnswer generated;
                        if (actualSearch) {
                            var provider = new FaqSearchAnswerProvider(searches,
                                    (request, results) -> generate(request.originalUserQuery(), results),
                                    ExecutionTrace.noop(), comparisonResolver,
                                    (id, question, candidates) -> candidateResolver.resolve(
                                            null, question, candidates));
                            generated = provider.generate(input);
                        } else {
                            generated = generate(input.originalUserQuery(), evidence(part.faqIds()));
                        }
                        calls.add(Map.of("question", input.originalUserQuery(),
                                "expected_faq_ids", part.faqIds(),
                                "actual_faq_ids", generated.sources().stream()
                                        .map(source -> source.faqId()).toList(),
                                "fallback_candidate_ids", actualSearch && generated.sources().isEmpty()
                                        ? searches.searchCandidates(new FaqSearchRequest(part.question(), 10))
                                                .stream().map(FaqSearchResponse::faqId).toList()
                                        : List.of(),
                                "answer", generated.answer().content(),
                                "answer_basis", generated.answer().answerBasis().name(),
                                "elapsed_ms", (System.nanoTime() - started) / 1_000_000));
                        return generated;
                    }, persistence, new ConfirmedConditionConverter(), events);

            processor.request(new ChatProcessingCommand(executionId, SESSION_ID, 1L, testCase.question()));
            ArgumentCaptor<ChatAnswer> combined = ArgumentCaptor.forClass(ChatAnswer.class);
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<com.telme.rag.dto.res.AnswerResult.AnswerSource>> sources =
                    ArgumentCaptor.forClass(List.class);
            org.mockito.Mockito.verify(persistence).persistFinalAnswers(eq(executionId), eq(SESSION_ID),
                    anyList(), combined.capture(), sources.capture());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", testCase.id());
            row.put("retrieval", actualSearch ? "ACTUAL_SEARCH" : "FORCED_GOLD");
            row.put("question", testCase.question());
            row.put("parts", calls);
            row.put("combined_answer", combined.getValue().content());
            row.put("combined_basis", combined.getValue().answerBasis().name());
            row.put("saved_faq_ids", sources.getValue().stream().map(source -> source.faqId()).toList());
            row.put("stream_events", sent);
            rows.add(row);
            persist(rows, actualSearch);
        }
    }

    private ConsultChatProcessingService.GeneratedAnswer generate(
            String question, List<FaqSearchResponse> evidence) {
        var answer = generator.generate(AnswerRequest.builder()
                .userQuery(question).conditions(Map.of()).searchResults(evidence).build(), SILENT);
        return new ConsultChatProcessingService.GeneratedAnswer(
                new ChatAnswer(ChatMessage.MessageType.ANSWER, answer.answer(),
                        answer.answerBasis(), List.of(), null), answer.sources());
    }

    private List<ConsultChatProcessingService.ConsultTurn> buildTurns(List<Part> parts) {
        List<ConsultChatProcessingService.ConsultTurn> turns = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            var decision = new DialogueDecision(100L + i, DialogueDecision.Action.PROCEED,
                    Map.of(), null, null, DialogueDecision.MessageOrigin.NONE);
            var prepared = new ConsultService.PreparedTurn(SESSION_ID, 1, decision);
            turns.add(new ConsultChatProcessingService.ConsultTurn(
                    new ConsultService.PreparationResult(prepared, null), parts.get(i).question()));
        }
        return turns;
    }

    private List<FaqSearchResponse> evidence(List<Long> ids) {
        Map<Long, Faq> byId = new LinkedHashMap<>();
        faqs.findAllById(ids).forEach(faq -> byId.put(faq.getFaqId(), faq));
        if (byId.size() != ids.size() || byId.values().stream().anyMatch(
                faq -> faq.getStatus() != Faq.Status.ACTIVE)) {
            throw new IllegalStateException("A fixed FAQ is missing or inactive");
        }
        List<FaqSearchResponse> results = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Faq faq = byId.get(ids.get(i));
            results.add(new FaqSearchResponse(faq.getFaqId(), faq.getSlotId(), faq.getCategory(),
                    faq.getQuestion(), faq.getAnswer(), 1.0, faq.getVersion(),
                    faq.getUpdatedAt() == null ? LocalDate.now()
                            : faq.getUpdatedAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),
                    i + 1, "FORCED_GOLD"));
        }
        return results;
    }

    private void persist(List<Map<String, Object>> rows, boolean actualSearch) throws Exception {
        Path path = Path.of(actualSearch
                ? System.getenv().getOrDefault("TELME_LIVE_MULTI_FAQ_OUT",
                        "scripts/compound-faq-evaluation/runs/compound-faq-live-current.json")
                : System.getenv().getOrDefault("TELME_FORCED_MULTI_FAQ_OUT",
                        "scripts/compound-faq-evaluation/runs/compound-faq-forced-current.json"));
        Files.createDirectories(path.getParent());
        Files.writeString(path, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(rows));
    }

    private record Case(String id, String question, List<Part> parts) {}
    private record Part(String question, List<Long> faqIds) {}
}
