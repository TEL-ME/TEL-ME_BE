package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.converter.ChatStoreConverter;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatCoordinates;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatFailure;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.converter.FollowupConditionConverter;
import com.telme.consult.dto.ClarificationPlan;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.dto.MissingCondition;
import com.telme.consult.service.ConsultChatProcessingService.AnswerInput;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.GeneratedAnswer;
import com.telme.consult.service.ConsultChatProcessingService.Prepared;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.ContextProvider;
import com.telme.intent.service.QueryRoutingService;
import com.telme.intent.service.RoutingPromptTemplates;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.dto.res.AnswerResult.AnswerSource;
import com.telme.store.service.LocationLookupCache;
import com.telme.store.service.LocationLookupResult;
import com.telme.store.service.StoreSearchService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=2")
class CompoundFaqStoreIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired QueryRoutingService routing;
    @Autowired ChatMessageRepository messages;
    @Autowired ContextProvider contexts;
    @Autowired ConsultTurnPreparationService preparation;
    @Autowired ConsultChatPersistenceService persistence;
    @Autowired StoreSearchService nearby;
    @Autowired NamedLocationStoreSearchAdapter named;
    @MockitoBean LlmClient llm;
    @MockitoBean LocationLookupCache locations;
    long userId;
    long sessionId;
    long storeId;
    long faqId;
    String initialRouting;
    boolean faqGrounded = true;
    // FAQ 조건 되묻기가 필요한 경우를 흉내 낸다. 기본은 되묻기 없음.
    ClarificationPlan faqPlan = ClarificationPlan.none();
    final List<AnswerInput> answerInputs = new ArrayList<>();
    final Events events = new Events();

    @BeforeEach
    void setup() {
        userId = jdbc.queryForObject("INSERT INTO users(email,name) VALUES (?,'복합 검증') RETURNING user_id",
                Long.class, UUID.randomUUID() + "@example.com");
        sessionId = jdbc.queryForObject("INSERT INTO chat_sessions(user_id) VALUES (?) RETURNING session_id",
                Long.class, userId);
        storeId = jdbc.queryForObject("""
                INSERT INTO stores(name,address,latitude,longitude,region_code)
                VALUES ('복합 검증 매장','검증 주소',37.501,127.021,'1168010100') RETURNING store_id
                """, Long.class);
        jdbc.update("""
                INSERT INTO store_services(store_id,service_type_id)
                SELECT ?,service_type_id FROM store_service_types WHERE code='PORT_IN'
                """, storeId);
        faqId = jdbc.queryForObject("INSERT INTO faqs(question,answer,category) VALUES (?,?,?) RETURNING faq_id",
                Long.class, "번호이동 방법", "신분증을 지참해 신청하세요.", "가입");
        when(locations.lookup("강남역")).thenReturn(Optional.of(new LocationLookupResult(
                "강남역", LocationLookupResult.Type.PLACE, 37.501, 127.021, null)));
        when(llm.generate(any())).thenAnswer(call -> {
            LlmRequest request = call.getArgument(0);
            if (RoutingPromptTemplates.ROUTING_SYSTEM_PROMPT.equals(request.systemPrompt())) {
                return initialRouting;
            }
            if (request.userPrompt().contains("강남역")) {
                return "{\"responseType\":\"CONDITION_RESPONSE\",\"conditions\":[{\"key\":\"location\",\"status\":\"FILLED\",\"value\":\"강남역\"}]}";
            }
            return "어느 지역의 매장을 찾으시나요?";
        });
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM consult_conditions WHERE consult_request_id IN"
                + " (SELECT consult_request_id FROM consult_requests WHERE session_id=?)", sessionId);
        jdbc.update("DELETE FROM consult_requests WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM chat_sessions WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM users WHERE user_id=?", userId);
        jdbc.update("DELETE FROM stores WHERE store_id=?", storeId);
        jdbc.update("DELETE FROM faqs WHERE faq_id=?", faqId);
    }

    @Test
    void personaGreetingPreservesDevelopFaqAndStoreCombination() throws Exception {
        route("강남역 번호이동 매장", Map.of("location", "강남역", "serviceType", "PORT_IN"));
        String original = "안녕 바라야, 번호이동 방법과 강남역 번호이동 매장 찾아줘";
        ChatProcessingCommand command = command(original, null);

        processor(false).request(command);

        assertCombined(command, true);
        assertThat(output(command).path("content").asText()).startsWith("안녕하세요! ");
        assertThat(messages.findById(command.inputMessageId()).orElseThrow().getContent()).isEqualTo(original);
    }

    @Test
    void faqAndRealStoreSearchCompleteOneAnswerWithSourcesAndMatchingSse() throws Exception {
        route("강남역 번호이동 매장", Map.of("location", "강남역", "serviceType", "PORT_IN"));
        ChatProcessingCommand command = command("번호이동 방법과 강남역 번호이동 매장 찾아줘", null);
        processor(false).request(command);

        assertCombined(command, true);
        assertThat(answerInputs).extracting(AnswerInput::purpose)
                .containsExactly(Purpose.GENERAL_FAQ, Purpose.NEARBY_STORE);
        assertThat(answerInputs.getFirst().confirmedConditions()).isEmpty();
        assertThat(answerInputs.get(1).confirmedConditions())
                .containsEntry("location", "강남역").containsEntry("serviceType", "PORT_IN");
        assertThat(answerInputs).allSatisfy(input -> assertThat(input.streamTokens()).isFalse());
    }

    // 답할 수 있는 FAQ는 바로 답하고, 매장 지역 되묻기를 같은 메시지 끝에 붙인다(복합 되묻기와 같은 동작).
    @Test
    void faqIsAnsweredNowAndStoreConditionsAreAskedInTheSameMessage() throws Exception {
        route("번호이동 가능한 매장", Map.of());
        var processor = processor(false);
        var initial = command("번호이동 방법과 번호이동 가능한 매장 찾아줘", null);
        processor.request(initial);
        var asked = output(initial);
        assertThat(asked.path("message_type").asText()).isEqualTo("CLARIFICATION");
        assertThat(asked.path("content").asText()).contains("1. 번호이동 방법", "신분증", "2. ", "어느 지역");
        assertThat(statuses()).containsExactly("DONE", "WAITING_CONDITION");
        assertThat(answerInputs).extracting(AnswerInput::purpose).containsExactly(Purpose.GENERAL_FAQ);
        // 하위 질문마다 고객에게 보인 결과를 기록한다(상담 종료 요약이 읽는다)
        assertThat(outcomes()).containsExactly("ANSWERED|번호이동 방법", "ASKED|번호이동 가능한 매장");
        assertThat(answerTexts().getFirst()).contains("신분증").doesNotContain("1. 번호이동 방법");
        assertThat(answerTexts().get(1)).contains("어느 지역").doesNotContain("신분증");
        long askedId = asked.path("message_id").asLong();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM message_sources WHERE message_id=? AND faq_id=?",
                Integer.class, askedId, faqId)).isEqualTo(1));

        var location = command("강남역", null);
        processor.request(location);
        assertThat(output(location).path("content").asText()).isEqualTo("어떤 업무로 매장을 찾으시나요?");
        assertThat(statuses()).containsExactly("DONE", "WAITING_CONDITION");

        var service = command("번호이동", null);
        processor.request(service);
        assertStoreOnly(service);
        assertThat(outcomes()).containsExactly("ANSWERED|번호이동 방법", "STORES|번호이동 가능한 매장");
        // 이미 답한 FAQ는 다시 생성하지 않는다.
        assertThat(answerInputs).extracting(AnswerInput::purpose)
                .containsExactly(Purpose.GENERAL_FAQ, Purpose.NEARBY_STORE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consult_conditions c JOIN consult_requests r"
                + " ON r.consult_request_id=c.consult_request_id WHERE r.session_id=?"
                + " AND c.condition_key IN ('location','serviceType') AND c.answered_message_id IS NOT NULL",
                Integer.class, sessionId)).isEqualTo(2);
    }

    @Test
    void gpsReplyToLocationClarificationResumesOriginalCompoundWithoutRerouting() throws Exception {
        route("번호이동 가능한 매장", Map.of("serviceType", "PORT_IN"));
        var processor = processor(false);
        processor.request(command("번호이동 방법과 번호이동 가능한 매장 찾아줘", null));
        // 좌표 응답에는 후속 분류 모델을 호출하지 않아야 한다.
        doReturn("{\"responseType\":\"NEW_QUESTION\",\"conditions\":[]}").when(llm).generate(any());
        var gps = command("현재 위치로 찾아줘", new ChatCoordinates(37.501, 127.021));
        processor.request(gps);

        assertStoreOnly(gps);
        assertThat(answerInputs.getLast().coordinates()).isEqualTo(gps.coordinates());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM query_routings q JOIN chat_messages m"
                + " ON m.message_id=q.message_id WHERE m.session_id=?", Integer.class, sessionId)).isEqualTo(1);
    }

    @Test
    void storeFailureKeepsTheFaqAnswerAndCompletesBothRequests() throws Exception {
        route("강남역 번호이동 매장", Map.of("location", "강남역", "serviceType", "PORT_IN"));
        when(locations.lookup("강남역")).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("장애"));
        var command = command("번호이동 방법과 강남역 번호이동 매장 찾아줘", null);
        processor(false).request(command);

        assertCombined(command, false);
        assertThat(output(command).path("content").asText()).contains("신분증", "잠시 후 다시 시도");
    }

    @Test
    void regionDeclineProvidesAlternativeWithoutDroppingFaq() throws Exception {
        route("매장 찾기", Map.of());
        var processor = processor(false);
        processor.request(command("번호이동 방법과 매장 찾기 알려줘", null));
        var declined = command("지역은 알려주고 싶지 않아요", null);
        // 거절 분석을 통제해 상담 상태 연결과 나머지 FAQ 보존을 검증한다.
        doReturn("{\"responseType\":\"CONDITION_RESPONSE\",\"conditions\":[{\"key\":\"location\",\"status\":\"DECLINED\",\"value\":null}]}")
                .when(llm).generate(any());
        processor.request(declined);

        assertThat(statuses()).containsExactly("DONE", "DONE");
        assertThat(output(declined).path("content").asText()).contains("검색 지역 없이는");
        assertThat(outcomes()).containsExactly("ANSWERED|번호이동 방법", "NO_EVIDENCE|매장 찾기");
        // FAQ는 처음 메시지에서 이미 답했다.
        assertThat(answerInputs).extracting(AnswerInput::purpose).containsExactly(Purpose.GENERAL_FAQ);
    }

    @Test
    void currentCoordinatesDoNotOverrideNamedLocationOfAnotherRequest() throws Exception {
        route("강남역 번호이동 매장", Map.of("location", "강남역", "serviceType", "PORT_IN"));
        var command = command("번호이동 방법과 강남역 번호이동 매장 찾아줘", new ChatCoordinates(37.6, 127.1));
        processor(false).request(command);

        assertCombined(command, true);
        assertThat(answerInputs.get(1).coordinates()).isNull();
    }

    @Test
    void generationFailureDoesNotSaveOrStreamAPartialCombinedAnswer() throws Exception {
        route("강남역 번호이동 매장", Map.of("location", "강남역", "serviceType", "PORT_IN"));
        var command = command("번호이동 방법과 강남역 번호이동 매장 찾아줘", null);
        processor(true).request(command);

        assertThat(jdbc.queryForObject("SELECT status FROM chat_executions WHERE execution_id=?",
                String.class, command.executionId())).isEqualTo("FAILED");
        assertThat(output(command).path("status").asText()).isEqualTo("FAILED");
        assertThat(events.tokens).isEmpty();
        assertThat(statuses()).containsExactly("PENDING", "PENDING");
    }

    @Test
    void missingFaqEvidenceStillReturnsActualStoreResults() throws Exception {
        faqGrounded = false;
        route("강남역 번호이동 매장", Map.of("location", "강남역", "serviceType", "PORT_IN"));
        var command = command("번호이동 방법과 강남역 번호이동 매장 찾아줘", null);
        processor(false).request(command);

        var output = output(command);
        assertThat(statuses()).containsExactly("DONE", "DONE");
        assertThat(output.path("content").asText()).contains(com.telme.rag.service.AnswerPromptTemplates.NO_EVIDENCE_ANSWER, "복합 검증 매장");
        assertThat(outcomes()).containsExactly("NO_EVIDENCE|번호이동 방법", "STORES|강남역 번호이동 매장");
        assertThat(answerTexts().getFirst()).isNull();
        assertThat(answerTexts().get(1)).contains("복합 검증 매장");
        assertThat(output.path("store_results").isArray()).isTrue();
        assertThat(output.path("answer_basis").isNull()).isTrue();
        assertThat(events.tokens).containsExactly(output.path("content").asText());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM message_sources WHERE message_id=?",
                Integer.class, output.path("message_id").asLong())).isZero();
    }

    @Test
    void storeWithLocationIsAnsweredAndOnlyTheMissingLocationIsAsked() throws Exception {
        initialRouting = mapper.writeValueAsString(Map.of("intent", "BOTH", "confidence", 0.99,
                "refinedQuery", "번호이동 안내와 매장 찾기", "subQueries", List.of(
                        Map.of("order", 1, "intent", "FAQ", "queryText", "번호이동 방법", "conditions", Map.of()),
                        Map.of("order", 2, "intent", "STORE", "queryText", "번호이동 매장",
                                "conditions", Map.of("serviceType", "PORT_IN")),
                        Map.of("order", 3, "intent", "STORE", "queryText", "신촌역 번호이동 매장",
                                "conditions", Map.of("location", "신촌역", "serviceType", "PORT_IN")))));
        when(locations.lookup("신촌역")).thenReturn(Optional.of(new LocationLookupResult(
                "신촌역", LocationLookupResult.Type.PLACE, 37.501, 127.021, null)));
        var processor = processor(false);
        var initial = command("번호이동 방법과 번호이동 매장, 신촌역 번호이동 매장 찾아줘", null);
        processor.request(initial);
        // 지역이 있는 매장 요청은 바로 답하고, 지역이 없는 요청만 되묻는다.
        assertThat(statuses()).containsExactly("DONE", "WAITING_CONDITION", "DONE");
        assertThat(output(initial).path("content").asText())
                .contains("신분증", "신촌역 기준", "복합 검증 매장", "어느 지역");
        var followup = command("강남역", null);
        processor.request(followup);

        assertStoreOnly(followup);
        assertThat(statuses()).containsExactly("DONE", "DONE", "DONE");
        assertThat(answerInputs).hasSize(3);
        assertThat(answerInputs.get(1).confirmedConditions()).containsEntry("location", "신촌역");
        assertThat(answerInputs.get(2).confirmedConditions()).containsEntry("location", "강남역");
    }

    // 지역이 빠진 매장 요청이 둘이면 한 메시지에 하나만 물을 수 없어 나눠 보내도록 안내한다.
    // FAQ 조건 되묻기와 매장 지역 되묻기가 동시에 필요하면, 조건 없이 답할 수 없는 매장을 묻고 FAQ는 조건 없이 답한다.
    @Test
    void storeLocationIsAskedBeforeFaqConditionWhenBothAreMissing() throws Exception {
        faqPlan = new ClarificationPlan(List.of(new MissingCondition(
                "unpaid_balance", "미납 요금이 있나요?", List.of("있어요", "없어요"), "합성 근거: 미납 요금은 먼저 완납한다.")));
        route("번호이동 가능한 매장", Map.of("serviceType", "PORT_IN"));
        var command = command("번호이동 방법과 번호이동 가능한 매장 찾아줘", null);
        processor(false).request(command);

        var asked = output(command);
        assertThat(asked.path("message_type").asText()).isEqualTo("CLARIFICATION");
        assertThat(asked.path("content").asText()).contains("1. 번호이동 방법", "신분증", "2. ", "어느 지역")
                .doesNotContain("미납 요금이 있나요?");
        assertThat(statuses()).containsExactly("DONE", "WAITING_CONDITION");
        assertThat(answerInputs).extracting(AnswerInput::purpose).containsExactly(Purpose.GENERAL_FAQ);
        assertThat(answerInputs.getFirst().confirmedConditions()).isEmpty();
    }

    @Test
    void twoStoresMissingLocationAskToSplitInsteadOfAskingTwice() throws Exception {
        initialRouting = mapper.writeValueAsString(Map.of("intent", "BOTH", "confidence", 0.99,
                "refinedQuery", "번호이동 안내와 매장 찾기", "subQueries", List.of(
                        Map.of("order", 1, "intent", "FAQ", "queryText", "번호이동 방법", "conditions", Map.of()),
                        Map.of("order", 2, "intent", "STORE", "queryText", "번호이동 매장",
                                "conditions", Map.of("serviceType", "PORT_IN")),
                        Map.of("order", 3, "intent", "STORE", "queryText", "유심 재발급 매장",
                                "conditions", Map.of("serviceType", "USIM_REISSUE")))));
        var command = command("번호이동 방법과 번호이동 매장, 유심 재발급 매장 찾아줘", null);
        processor(false).request(command);

        var output = output(command);
        assertThat(output.path("message_type").asText()).isEqualTo("ANSWER");
        assertThat(output.path("content").asText()).isEqualTo(ConsultChatProcessingService.SPLIT_STORE_GUIDANCE);
        assertThat(answerInputs).isEmpty();
        // 처리하지 않은 하위 상담을 열린 상태로 남기지 않는다.
        assertThat(statuses()).containsOnly("CANCELLED");
        assertThat(outcomes()).containsExactly("SPLIT_REFUSED|번호이동 방법", "SPLIT_REFUSED|번호이동 매장",
                "SPLIT_REFUSED|유심 재발급 매장");
    }

    @Test
    void clarificationVersionConflictRollsBackSiblingChangesAndMessage() throws Exception {
        route("매장", Map.of());
        var command = command("번호이동 방법과 매장 찾아줘", null);
        var queries = routing.routeSingleConsult(messages.findByIdWithSession(command.inputMessageId()).orElseThrow(), null)
                .subQueries();
        var first = preparation.prepareAnalysis(sessionId, queries.getFirst(),
                com.telme.consult.dto.DialogueInput.LocationStatus.MISSING);
        var second = preparation.prepareAnalysis(sessionId, queries.get(1),
                com.telme.consult.dto.DialogueInput.LocationStatus.MISSING);
        int version = first.prepared().expectedVersion();
        var stale = new ConsultService.PreparationResult(new ConsultService.PreparedTurn(sessionId,
                second.prepared().expectedVersion() + 1, second.prepared().decision()), null);
        assertThatThrownBy(() -> persistence.persistCompoundClarification(command.executionId(), sessionId,
                List.of(first.prepared()), stale.prepared(), "1. 번호이동 방법\n안내\n\n2. 매장 찾기\n어느 지역인가요?",
                List.of(), List.of()))
                .isInstanceOf(RuntimeException.class);
        assertThat(statuses()).containsExactly("PENDING", "PENDING");
        assertThat(jdbc.queryForObject("SELECT version FROM consult_requests WHERE consult_request_id=?",
                Integer.class, queries.getFirst().consultRequestId())).isEqualTo(version);
        assertThat(jdbc.queryForObject("SELECT output_message_id FROM chat_executions WHERE execution_id=?",
                Long.class, command.executionId())).isNull();
    }

    private void route(String storeQuery, Map<String, String> conditions) throws Exception {
        initialRouting = mapper.writeValueAsString(Map.of("intent", "BOTH", "confidence", 0.99,
                "refinedQuery", "번호이동 방법과 매장 찾기", "subQueries", List.of(
                        Map.of("order", 1, "intent", "FAQ", "queryText", "번호이동 방법", "conditions", Map.of()),
                        Map.of("order", 2, "intent", "STORE", "queryText", storeQuery, "conditions", conditions))));
    }

    private ConsultChatProcessingService processor(boolean failStore) {
        var analyzer = new ConsultTurnAnalysisAdapter(contexts,
                new QueryRoutingAnalysisProvider(messages, routing, new QueryRoutingFollowupAnalysisProvider(routing)),
                preparation, new FollowupConditionConverter(), null);
        var stores = new ChatStoreAnswerProvider(nearby, named, new ChatStoreConverter());
        AnswerProvider faqAnswers = new AnswerProvider() {
            @Override
            public GeneratedAnswer generate(AnswerInput input) {
                answerInputs.add(input);
                if (!faqGrounded) {
                    return GeneratedAnswer.withoutSources(new ChatAnswer(ChatMessage.MessageType.ANSWER,
                            com.telme.rag.service.AnswerPromptTemplates.NO_EVIDENCE_ANSWER,
                            ChatMessage.AnswerBasis.NO_EVIDENCE, List.of(), null));
                }
                return new GeneratedAnswer(new ChatAnswer(ChatMessage.MessageType.ANSWER,
                        "신분증을 지참해 신청하세요.", ChatMessage.AnswerBasis.GROUNDED, List.of(), null),
                        List.of(new AnswerSource(faqId, "번호이동 안내", 1, null, (short) 1, null)));
            }

            @Override
            public boolean clarifies() {
                return faqPlan.needsClarification();
            }

            @Override
            public Prepared prepare(AnswerInput input, boolean plansClarification) {
                return new Prepared(List.of(), faqPlan);
            }
        };
        var answers = new PurposeRoutingAnswerProvider(faqAnswers, input -> {
            answerInputs.add(input);
            if (failStore) {
                throw new IllegalStateException("변환 실패");
            }
            return stores.generate(input);
        });
        return new ConsultChatProcessingService(analyzer, answers, persistence,
                new ConfirmedConditionConverter(), events);
    }

    private ChatProcessingCommand command(String content, ChatCoordinates coordinates) {
        long messageId = jdbc.queryForObject("""
                INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,status,completed_at)
                SELECT ?,coalesce(max(sequence_no),0)+1,'USER','QUESTION',?,'COMPLETED',now()
                FROM chat_messages WHERE session_id=? RETURNING message_id
                """, Long.class, sessionId, content, sessionId);
        long executionId = jdbc.queryForObject("INSERT INTO chat_executions(session_id,input_message_id,status)"
                + " VALUES (?,?,'RUNNING') RETURNING execution_id", Long.class, sessionId, messageId);
        return new ChatProcessingCommand(executionId, sessionId, messageId, content, coordinates);
    }

    private com.fasterxml.jackson.databind.JsonNode output(ChatProcessingCommand command) throws Exception {
        return mapper.readTree(jdbc.queryForObject("SELECT row_to_json(m)::text FROM chat_messages m"
                + " JOIN chat_executions e ON e.output_message_id=m.message_id WHERE e.execution_id=?",
                String.class, command.executionId()));
    }

    private List<String> statuses() {
        return jdbc.queryForList("SELECT status FROM consult_requests WHERE session_id=? ORDER BY subquery_order",
                String.class, sessionId);
    }

    // 하위 질문 순서대로 "결과|소제목". 소제목이 없으면(단일 질문) 결과만
    private List<String> outcomes() {
        return jdbc.queryForList("SELECT answer_outcome || coalesce('|' || display_question, '')"
                + " FROM consult_requests WHERE session_id=? ORDER BY subquery_order", String.class, sessionId);
    }

    private List<String> answerTexts() {
        return jdbc.queryForList("SELECT answer_text FROM consult_requests WHERE session_id=? ORDER BY subquery_order",
                String.class, sessionId);
    }

    private void assertCombined(ChatProcessingCommand command, boolean hasStores) throws Exception {
        var output = output(command);
        assertThat(statuses()).containsExactly("DONE", "DONE");
        assertThat(output.path("message_type").asText()).isEqualTo("ANSWER");
        assertThat(output.path("content").asText()).contains("1. 번호이동 방법", "2. ", "신분증");
        assertThat(output.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(events.tokens).containsExactly(output.path("content").asText());
        assertThat(output.path("store_results").isArray()).isEqualTo(hasStores);
        if (hasStores) {
            assertThat(output.path("store_results").toString()).contains("복합 검증 매장");
        }
        long outputId = output.path("message_id").asLong();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM message_sources WHERE message_id=? AND faq_id=?",
                Integer.class, outputId, faqId)).isEqualTo(1));
    }

    // 되묻기 뒤 이어진 매장 답변은 매장 요청 하나만 완료한다.
    private void assertStoreOnly(ChatProcessingCommand command) throws Exception {
        var output = output(command);
        assertThat(output.path("message_type").asText()).isEqualTo("STORE_RESULT");
        assertThat(output.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(output.path("store_results").toString()).contains("복합 검증 매장");
        assertThat(statuses()).doesNotContain("WAITING_CONDITION", "PENDING");
    }

    private static class Events implements ConsultChatEvents {
        final List<String> tokens = new ArrayList<>();
        public void started(ChatExecutionState state) {}
        public void completed(long executionId, ChatExecutionState state) {}
        public void failed(long executionId, ChatFailure failure) {}
        public LlmStreamHandler stream(long executionId) {
            return new LlmStreamHandler() {
                public void onToken(String token) { tokens.add(token); }
                public void onComplete() {}
                public void onError(Throwable error) {}
            };
        }
    }
}
