package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatFailure;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatExecutionTraceService;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.converter.FollowupConditionConverter;
import com.telme.consult.dto.ClarificationPlan;
import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.MessageOrigin;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.dto.MissingCondition;
import com.telme.consult.repository.JdbcConsultStateStore;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.intent.service.QueryRoutingService;
import com.telme.llm.service.LlmClient;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.dto.res.AnswerResult.AnswerSource;
import com.telme.rag.service.AnswerPromptTemplates;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SpringBootTest(properties = {
        "telme.consult.persistence-enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"
})
class ConsultChatPersistenceIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsultService consult;
    @Autowired JdbcConsultStateStore states;
    @Autowired ConsultChatPersistenceService persistence;
    @Autowired ConsultTurnPreparationService preparation;
    @Autowired ChatMessageRepository messages;
    @Autowired QueryRoutingService routing;
    @Autowired ChatExecutionTraceService trace;
    @MockitoBean LlmClient llmClient;
    long userId;
    long sessionId;
    long executionId;
    long requestId;
    final List<Long> comparisonFaqIds = new ArrayList<>();

    @BeforeEach
    void setup() {
        userId =
                jdbc.queryForObject(
                        "INSERT INTO users(email,name) VALUES (?,'연결') RETURNING user_id",
                        Long.class,
                        "consult-link-" + UUID.randomUUID() + "@example.com");
        sessionId =
                jdbc.queryForObject(
                        "INSERT INTO chat_sessions(user_id) VALUES (?) RETURNING session_id",
                        Long.class,
                        userId);
        long input =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " chat_messages(session_id,sequence_no,role,message_type,content,status,completed_at)"
                            + " VALUES (?,1,'USER','QUESTION','매장 알려줘','COMPLETED',now()) RETURNING"
                            + " message_id",
                        Long.class,
                        sessionId);
        executionId =
                jdbc.queryForObject(
                        "INSERT INTO chat_executions(session_id,input_message_id,status) VALUES"
                                + " (?,?,'RUNNING') RETURNING execution_id",
                        Long.class,
                        sessionId,
                        input);
        requestId =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)"
                            + " VALUES (?,?,1,'STORE','매장') RETURNING consult_request_id",
                        Long.class,
                        sessionId,
                        input);
    }

    @AfterEach
    void cleanup() {
        jdbc.update(
                "DELETE FROM consult_conditions WHERE consult_request_id IN (SELECT"
                        + " consult_request_id FROM consult_requests WHERE session_id IN (SELECT"
                        + " session_id FROM chat_sessions WHERE user_id=?))",
                userId);
        jdbc.update(
                "DELETE FROM consult_requests WHERE session_id IN (SELECT session_id FROM"
                        + " chat_sessions WHERE user_id=?)",
                userId);
        jdbc.update("DELETE FROM chat_sessions WHERE user_id=?", userId);
        comparisonFaqIds.forEach(id -> jdbc.update("DELETE FROM faqs WHERE faq_id=?", id));
        comparisonFaqIds.clear();
        jdbc.update("DELETE FROM users WHERE user_id=?", userId);
    }

    @Test
    void savesMessageExecutionAndConditionTogether() {
        var prepared =
                consult.prepare(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of(),
                        LocationStatus.MISSING);
        var output = persistence.persistClarification(executionId, prepared);
        assertThat(states.findPendingClarificationMessageId(sessionId, requestId, "location"))
                .contains(output.outputMessage().messageId());
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("COMPLETED");
        assertThat(text("SELECT status FROM chat_sessions WHERE session_id=?", sessionId))
                .isEqualTo("NEED_CLARIFICATION");
        assertThat(states.load(sessionId, requestId).version()).isEqualTo(2);
    }

    @Test
    void savesClarificationPlanForTheNextQuestion() {
        var plan = new ClarificationPlan(List.of(
                new MissingCondition(
                        "joined_this_month", "이번 달에 가입하셨나요?", List.of("예", "아니요"), "가입 시점에 따라 다릅니다."),
                new MissingCondition(
                        "changed_this_month", "이번 달에 요금제를 바꾸셨나요?", List.of("예", "아니요"), "변경 시점에 따라 다릅니다.")));
        var snapshot = states.load(sessionId, requestId);
        var prepared = new ConsultService.PreparedTurn(
                sessionId,
                snapshot.version(),
                FaqClarificationDecisions.ask(requestId, plan, Map.of()),
                plan);

        persistence.persistClarification(executionId, prepared);

        assertThat(states.load(sessionId, requestId).clarificationPlan().conditions())
                .extracting(MissingCondition::key)
                .containsExactly("joined_this_month", "changed_this_month");
    }

    @Test
    void versionConflictRollsBackFlushedMessageAndExecution() {
        var prepared =
                consult.prepare(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of(),
                        LocationStatus.MISSING);
        jdbc.update(
                "UPDATE consult_requests SET version=version+1 WHERE consult_request_id=?",
                requestId);
        assertThatThrownBy(() -> persistence.persistClarification(executionId, prepared))
                .isInstanceOf(GeneralException.class);
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("RUNNING");
        assertThat(text("SELECT status FROM chat_sessions WHERE session_id=?", sessionId))
                .isEqualTo("ACTIVE");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM chat_messages WHERE session_id=?",
                                Integer.class,
                                sessionId))
                .isEqualTo(1);
        assertThat(states.load(sessionId, requestId).status()).isEqualTo("PENDING");
    }

    @Test
    void finishedExecutionDoesNotSaveConsultation() {
        var prepared =
                consult.prepare(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of(),
                        LocationStatus.MISSING);
        jdbc.update("UPDATE chat_executions SET status='FAILED' WHERE execution_id=?", executionId);
        assertThatThrownBy(() -> persistence.persistClarification(executionId, prepared))
                .isInstanceOf(GeneralException.class);
        assertThat(states.load(sessionId, requestId).version()).isEqualTo(1);
    }

    @Test
    void followupFillsExistingRequestAndKeepsExecutionRunning() {
        long inputId = createFollowup();
        var prepared =
                consult.prepare(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("강남역")),
                        LocationStatus.MISSING);
        persistence.persistReadyFollowup(executionId, prepared, "location");
        assertThat(states.load(sessionId, requestId).conditions().get("location"))
                .isEqualTo(Condition.filled("강남역"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT answered_message_id FROM consult_conditions WHERE"
                                        + " consult_request_id=? AND condition_key='location'",
                                Long.class,
                                requestId))
                .isEqualTo(inputId);
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("RUNNING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM consult_requests WHERE session_id=?",
                                Integer.class,
                                sessionId))
                .isEqualTo(1);
    }

    @Test
    void staleFollowupDoesNotOverwriteWaitingCondition() {
        createFollowup();
        var prepared =
                consult.prepare(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("강남역")),
                        LocationStatus.MISSING);
        jdbc.update(
                "UPDATE consult_requests SET version=version+1 WHERE consult_request_id=?",
                requestId);
        assertThatThrownBy(
                        () -> persistence.persistReadyFollowup(executionId, prepared, "location"))
                .isInstanceOf(GeneralException.class);
        assertThat(states.load(sessionId, requestId).conditions().get("location"))
                .isEqualTo(Condition.pending());
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("RUNNING");
    }

    @Test
    void closedChatRejectsFollowup() {
        createFollowup();
        var prepared =
                consult.prepare(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("강남역")),
                        LocationStatus.MISSING);
        jdbc.update("UPDATE chat_sessions SET status='CLOSED' WHERE session_id=?", sessionId);
        assertThatThrownBy(
                        () -> persistence.persistReadyFollowup(executionId, prepared, "location"))
                .isInstanceOf(GeneralException.class);
        assertThat(states.load(sessionId, requestId).conditions().get("location"))
                .isEqualTo(Condition.pending());
    }

    @Test
    void waitingCorrectionKeepsQuestionAndCompletesExecution() {
        createFollowup();
        var result =
                consult.prepareTurn(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of("serviceType", Condition.filled("USIM_REISSUE")),
                        LocationStatus.MISSING);
        assertThat(result.waitingForReply()).isTrue();
        assertThat(result.prepared()).isNotNull();
        persistence.persistWaiting(executionId, sessionId, result);
        assertThat(states.load(sessionId, requestId).conditions().get("serviceType"))
                .isEqualTo(Condition.filled("USIM_REISSUE"));
        assertThat(states.findPendingClarificationMessageId(sessionId, requestId, "location"))
                .contains(result.pendingMessageId());
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("COMPLETED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM chat_messages WHERE session_id=?",
                                Integer.class,
                                sessionId))
                .isEqualTo(3);
    }

    @Test
    void unchangedWaitingCompletesWithoutAnotherQuestion() {
        createFollowup();
        var result =
                consult.prepareTurn(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of(),
                        LocationStatus.MISSING);
        int version = states.load(sessionId, requestId).version();
        persistence.persistWaiting(executionId, sessionId, result);
        assertThat(states.load(sessionId, requestId).version()).isEqualTo(version);
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("COMPLETED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM chat_messages WHERE session_id=?",
                                Integer.class,
                                sessionId))
                .isEqualTo(3);
    }

    @Test
    void staleWaitingCorrectionRollsBackExecutionCompletion() {
        createFollowup();
        var result =
                consult.prepareTurn(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of("serviceType", Condition.filled("USIM_REISSUE")),
                        LocationStatus.MISSING);
        jdbc.update(
                "UPDATE consult_requests SET version=version+1 WHERE consult_request_id=?",
                requestId);
        assertThatThrownBy(() -> persistence.persistWaiting(executionId, sessionId, result))
                .isInstanceOf(GeneralException.class);
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("RUNNING");
        assertThat(states.load(sessionId, requestId).conditions()).doesNotContainKey("serviceType");
    }

    @Test
    void finalAnswerCompletesChatAndConsultationTogether() {
        createFollowup();
        var prepared =
                consult.prepare(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("강남역")),
                        LocationStatus.MISSING);
        persistence.persistReadyFollowup(executionId, prepared, "location");
        int version = states.load(sessionId, requestId).version();
        var output =
                persistence.persistFinalAnswer(
                        executionId,
                        sessionId,
                        requestId,
                        version,
                        new ChatAnswer(
                                ChatMessage.MessageType.ANSWER,
                                "강남역 매장을 안내해드릴게요.",
                                null,
                                List.of(),
                                null));
        assertThat(states.load(sessionId, requestId).status()).isEqualTo("DONE");
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("COMPLETED");
        assertThat(text("SELECT status FROM chat_sessions WHERE session_id=?", sessionId))
                .isEqualTo("ACTIVE");
        assertThat(
                        text(
                                "SELECT status FROM chat_messages WHERE message_id=?",
                                output.outputMessage().messageId()))
                .isEqualTo("COMPLETED");
    }

    @Test
    void startAnswerCreatesGeneratingMessageAndRejectsAnotherSession() {
        var output = persistence.startAnswer(executionId, sessionId);
        assertThat(output.outputMessage().status()).isEqualTo(ChatMessage.Status.GENERATING);
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("RUNNING");

        long anotherExecution =
                jdbc.queryForObject(
                        "INSERT INTO chat_executions(session_id,input_message_id,status)"
                                + " SELECT session_id,input_message_id,'RUNNING' FROM"
                                + " chat_executions WHERE execution_id=? RETURNING execution_id",
                        Long.class,
                        executionId);
        assertThatThrownBy(() -> persistence.startAnswer(anotherExecution, sessionId + 1))
                .isInstanceOf(GeneralException.class);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT output_message_id FROM chat_executions WHERE execution_id=?",
                                Long.class,
                                anotherExecution))
                .isNull();
    }

    @Test
    void processingPortCompletesUnknownGuidanceWithoutConsultPreparation() {
        var answer =
                new ChatAnswer(
                        ChatMessage.MessageType.ANSWER,
                        "통신 관련 질문을 입력해 주세요.",
                        ChatMessage.AnswerBasis.OUT_OF_SCOPE,
                        List.of(),
                        null);
        var events = new RecordingEvents();
        var processor =
                new ConsultChatProcessingService(
                        command -> ConsultChatProcessingService.AnalyzedTurn.direct(answer),
                        input -> {
                            throw new AssertionError("UNKNOWN 안내에서는 RAG를 호출하지 않는다.");
                        },
                        persistence,
                        new ConfirmedConditionConverter(),
                        events);

        processor.request(processingCommand());

        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("COMPLETED");
        assertThat(
                        text(
                                "SELECT content FROM chat_messages WHERE message_id=(SELECT"
                                        + " output_message_id FROM chat_executions WHERE"
                                        + " execution_id=?)",
                                executionId))
                .isEqualTo("통신 관련 질문을 입력해 주세요.");
        assertThat(events.sequence).containsExactly("complete:COMPLETED");
    }

    @Test
    void processingPortSavesClarificationWithoutSearching() {
        var prepared =
                consult.prepareTurn(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of(),
                        LocationStatus.MISSING);
        var processor =
                new ConsultChatProcessingService(
                        command ->
                                new ConsultChatProcessingService.AnalyzedTurn(
                                        prepared,
                                        null,
                                        Purpose.NEARBY_STORE,
                                        "매장 알려줘",
                                        "매장"),
                        input -> {
                            throw new AssertionError("되묻기에서는 검색하지 않는다.");
                        },
                        persistence,
                        new ConfirmedConditionConverter());
        processor.request(processingCommand());
        assertThat(states.load(sessionId, requestId).status()).isEqualTo("WAITING_CONDITION");
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("COMPLETED");
    }

    @Test
    void processingPortAsksTheNextStoredConditionWithoutExtractingAgain() {
        var plan = new ClarificationPlan(List.of(
                new MissingCondition(
                        "joined_this_month", "이번 달에 가입하셨나요?", List.of("예", "아니요"), "가입 시점에 따라 다릅니다."),
                new MissingCondition(
                        "changed_this_month", "이번 달에 요금제를 바꾸셨나요?", List.of("예", "아니요"), "변경 시점에 따라 다릅니다.")));
        var snapshot = states.load(sessionId, requestId);
        var proceed = new DialogueDecision(
                requestId,
                DialogueDecision.Action.PROCEED,
                Map.of(
                        "joined_this_month", Condition.filled("예"),
                        "changed_this_month", Condition.pending()),
                null,
                null,
                MessageOrigin.NONE);
        var prepared = new ConsultService.PreparationResult(
                new ConsultService.PreparedTurn(sessionId, snapshot.version(), proceed, plan), null);
        var processor = new ConsultChatProcessingService(
                command -> new ConsultChatProcessingService.AnalyzedTurn(
                        prepared, null, Purpose.GENERAL_FAQ, "요금제 할인", "요금제 할인"),
                input -> {
                    throw new AssertionError("저장한 다음 조건을 물을 때는 검색·조건 재추출을 하지 않는다.");
                },
                persistence,
                new ConfirmedConditionConverter());

        processor.request(processingCommand());

        var saved = states.load(sessionId, requestId);
        assertThat(saved.status()).isEqualTo("WAITING_CONDITION");
        assertThat(saved.conditions().get("joined_this_month").value()).isEqualTo("예");
        assertThat(saved.clarificationPlan().conditions())
                .extracting(MissingCondition::key)
                .containsExactly("changed_this_month");
        assertThat(jdbc.queryForObject(
                "SELECT content FROM chat_messages WHERE message_id=(SELECT output_message_id"
                        + " FROM chat_executions WHERE execution_id=?)",
                String.class,
                executionId))
                .isEqualTo("이번 달에 요금제를 바꾸셨나요?");
    }

    @Test
    void processingPortCompletesAnswerAfterConditionsAreReady() {
        var prepared =
                consult.prepareTurn(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("강남역")),
                        LocationStatus.MISSING);
        var events = new RecordingEvents();
        var processor =
                new ConsultChatProcessingService(
                        command ->
                                new ConsultChatProcessingService.AnalyzedTurn(
                                        prepared,
                                        null,
                                        Purpose.NEARBY_STORE,
                                        "매장 알려줘",
                                        "매장"),
                        input -> {
                            assertThat(input.originalUserQuery()).isEqualTo("매장 알려줘");
                            assertThat(input.searchQuery()).isEqualTo("매장");
                            assertThat(input.confirmedConditions())
                                    .containsEntry("location", "강남역");
                            assertThat(
                                            text(
                                                    "SELECT status FROM chat_executions WHERE"
                                                            + " execution_id=?",
                                                    executionId))
                                    .isEqualTo("RUNNING");
                            assertThat(
                                            text(
                                                    "SELECT status FROM chat_messages WHERE"
                                                            + " message_id=(SELECT output_message_id"
                                                            + " FROM chat_executions WHERE"
                                                            + " execution_id=?)",
                                                    executionId))
                                    .isEqualTo("GENERATING");
                            assertThat(events.sequence).containsExactly("start");
                            return new ConsultChatProcessingService.GeneratedAnswer(
                                    new ChatAnswer(
                                            ChatMessage.MessageType.ANSWER,
                                            "매장 안내",
                                            null,
                                            List.of(),
                                            null),
                                    List.of(
                                            new AnswerSource(
                                                    null, "매장 FAQ", 1, null, (short) 1, null)));
                        },
                        persistence,
                        new ConfirmedConditionConverter(),
                        events);
        processor.request(processingCommand());
        assertThat(states.load(sessionId, requestId).status()).isEqualTo("DONE");
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("COMPLETED");
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.queryForObject(
                                                        "SELECT count(*) FROM message_sources"
                                                                + " WHERE message_id=(SELECT"
                                                                + " output_message_id FROM"
                                                                + " chat_executions WHERE"
                                                                + " execution_id=?)",
                                                        Integer.class,
                                                        executionId))
                                        .isEqualTo(1));
        assertThat(events.sequence)
                .containsExactly(
                        "start", "token:매장 안내", "complete:COMPLETED");
    }

    @Test
    void independentFaqAndGeneralComparisonSaveOneAnswerWithBothSidesAndMatchingTokens() {
        long inputId = jdbc.queryForObject(
                "SELECT input_message_id FROM chat_executions WHERE execution_id=?", Long.class, executionId);
        String firstQuery = "유심 재발급 비용";
        String comparison = "청소년 요금제와 시니어 요금제 차이점";
        jdbc.update("UPDATE consult_requests SET intent='FAQ',query_text=? WHERE consult_request_id=?",
                firstQuery, requestId);
        long secondId = jdbc.queryForObject(
                "INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)"
                        + " VALUES (?,?,2,'FAQ',?) RETURNING consult_request_id",
                Long.class, sessionId, inputId, comparison);
        var first = consult.prepareTurn(sessionId, requestId, Purpose.GENERAL_FAQ, Map.of(), LocationStatus.MISSING);
        var second = consult.prepareTurn(sessionId, secondId, Purpose.GENERAL_FAQ, Map.of(), LocationStatus.MISSING);
        var fee = comparisonFaq(firstQuery, "유심 재발급 비용은 7,700원입니다.");
        var youth = comparisonFaq("청소년 요금제 가입 조건", "청소년 요금제는 만 18세 이하가 가입할 수 있습니다.");
        var senior = comparisonFaq("시니어 요금제 가입 조건", "시니어 요금제는 만 65세 이상이 가입할 수 있습니다.");
        when(llmClient.generate(any())).thenReturn("""
                {"answerable":true,"leftTarget":"청소년 요금제","rightTarget":"시니어 요금제",
                 "criterion":"가입 나이 기준","leftFaqId":%d,"leftQuote":"%s",
                 "rightFaqId":%d,"rightQuote":"%s"}
                """.formatted(youth.faqId(), youth.answer(), senior.faqId(), senior.answer()));
        var searches = mock(FaqSearchService.class);
        when(searches.search(any())).thenAnswer(invocation -> {
            String query = ((FaqSearchRequest) invocation.getArgument(0)).query();
            return query.equals(firstQuery) ? List.of(fee) : List.of(youth, senior);
        });
        var answers = new FaqSearchAnswerProvider(searches, (input, results) -> {
            assertThat(input.originalUserQuery()).isEqualTo(firstQuery);
            return new ConsultChatProcessingService.GeneratedAnswer(new ChatAnswer(
                    ChatMessage.MessageType.ANSWER, fee.answer(), ChatMessage.AnswerBasis.GROUNDED, List.of(), null),
                    List.of(new AnswerSource(fee.faqId(), fee.answer(), 1, null, (short) 1, null)));
        }, trace, new LlmComparisonEvidenceResolver(llmClient, new ObjectMapper()));
        var events = new RecordingEvents();
        var processor = new ConsultChatProcessingService(
                command -> ConsultChatProcessingService.AnalyzedTurn.multipleFaq(List.of(
                        new ConsultChatProcessingService.FaqTurn(first, firstQuery),
                        new ConsultChatProcessingService.FaqTurn(second, comparison))),
                answers, persistence, new ConfirmedConditionConverter(), events, trace);

        processor.request(processingCommand());

        assertThat(states.load(sessionId, requestId).status()).isEqualTo("DONE");
        assertThat(states.load(sessionId, secondId).status()).isEqualTo("DONE");
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("COMPLETED");
        String content = text("SELECT content FROM chat_messages WHERE message_id="
                + "(SELECT output_message_id FROM chat_executions WHERE execution_id=?)", executionId);
        assertThat(content).contains(fee.answer(), youth.answer(), senior.answer())
                .doesNotContain(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        assertThat(content.indexOf(fee.answer())).isLessThan(content.indexOf(youth.answer()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_messages WHERE session_id=? AND role='ASSISTANT'",
                Integer.class, sessionId)).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForList(
                "SELECT faq_id FROM message_sources WHERE message_id="
                        + "(SELECT output_message_id FROM chat_executions WHERE execution_id=?)",
                Long.class, executionId)).containsExactlyInAnyOrder(fee.faqId(), youth.faqId(), senior.faqId()));
        assertThat(events.sequence).containsExactly("start", "token:" + content, "complete:COMPLETED");
    }

    private FaqSearchResponse comparisonFaq(String question, String answer) {
        long id = jdbc.queryForObject("INSERT INTO faqs(category,question,answer,slot_id)"
                + " VALUES ('SERVICE',?,?,?) RETURNING faq_id", Long.class,
                question + " " + UUID.randomUUID(), answer, "comparison-" + UUID.randomUUID());
        comparisonFaqIds.add(id);
        return new FaqSearchResponse(id, null, "SERVICE", question, answer, 0.9, 1, null, 1, null);
    }

    @Test
    void multipleFaqQuestionsSearchSeparatelyAndCompleteOneAnswer() {
        long inputId = jdbc.queryForObject(
                "SELECT input_message_id FROM chat_executions WHERE execution_id=?",
                Long.class, executionId);
        jdbc.update("UPDATE consult_requests SET intent='FAQ',query_text='요금제 종류' WHERE consult_request_id=?",
                requestId);
        long secondId = jdbc.queryForObject(
                "INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)"
                        + " VALUES (?,?,2,'FAQ','로밍 신청 방법') RETURNING consult_request_id",
                Long.class, sessionId, inputId);
        var first = consult.prepareTurn(sessionId, requestId, Purpose.GENERAL_FAQ,
                Map.of(), LocationStatus.MISSING);
        var second = consult.prepareTurn(sessionId, secondId, Purpose.GENERAL_FAQ,
                Map.of(), LocationStatus.MISSING);
        var searches = mock(FaqSearchService.class);
        List<String> searched = new ArrayList<>();
        when(searches.search(any(FaqSearchRequest.class))).thenAnswer(invocation -> {
            FaqSearchRequest request = invocation.getArgument(0);
            searched.add(request.query());
            return List.of(new FaqSearchResponse(null, null, "SERVICE", request.query(),
                    request.query() + "에 대한 근거", 0.9, 1, null, 1, null));
        });
        var events = new RecordingEvents();
        var answers = new FaqSearchAnswerProvider(searches, (input, results) -> {
            assertThat(input.streamTokens()).isFalse();
            assertThat(results).hasSize(1);
            String query = results.getFirst().question();
            return new ConsultChatProcessingService.GeneratedAnswer(
                    new ChatAnswer(ChatMessage.MessageType.ANSWER, query + " 답변",
                            ChatMessage.AnswerBasis.GROUNDED, List.of(), null),
                    List.of(new AnswerSource(null, query, 1, null, (short) 1, null)));
        });
        var processor = new ConsultChatProcessingService(
                command -> ConsultChatProcessingService.AnalyzedTurn.multipleFaq(List.of(
                        new ConsultChatProcessingService.FaqTurn(first, "요금제 종류"),
                        new ConsultChatProcessingService.FaqTurn(second, "로밍 신청 방법"))),
                answers, persistence, new ConfirmedConditionConverter(), events, trace);

        processor.request(processingCommand());

        assertThat(searched).containsExactly("요금제 종류", "로밍 신청 방법");
        assertThat(text("SELECT pipeline_trace->'finalTransmission'->>'status'"
                + " FROM chat_executions WHERE execution_id=?", executionId)).isEqualTo("DISPATCH_RETURNED");
        assertThat(jdbc.queryForObject("SELECT (pipeline_trace->'finalTransmission'->>'outputMessageId')::bigint"
                + " = output_message_id FROM chat_executions WHERE execution_id=?", Boolean.class, executionId))
                .isTrue();
        assertThat(states.load(sessionId, requestId).status()).isEqualTo("DONE");
        assertThat(states.load(sessionId, secondId).status()).isEqualTo("DONE");
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("COMPLETED");
        String content = jdbc.queryForObject(
                "SELECT content FROM chat_messages WHERE message_id=(SELECT output_message_id"
                        + " FROM chat_executions WHERE execution_id=?)", String.class, executionId);
        assertThat(content).contains("1. 요금제 종류\n요금제 종류 답변",
                "2. 로밍 신청 방법\n로밍 신청 방법 답변");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM chat_messages WHERE session_id=? AND role='ASSISTANT'",
                Integer.class, sessionId)).isEqualTo(1);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM message_sources WHERE message_id=(SELECT output_message_id"
                        + " FROM chat_executions WHERE execution_id=?)", Integer.class, executionId))
                .isEqualTo(2));
        assertThat(events.sequence).containsExactly("start", "token:" + content,
                "complete:COMPLETED");
    }

    @Test
    void missingSubQuestionDoesNotBorrowSiblingEvidenceFromWholeQuestion() {
        long inputId = jdbc.queryForObject(
                "SELECT input_message_id FROM chat_executions WHERE execution_id=?",
                Long.class, executionId);
        jdbc.update("UPDATE consult_requests SET intent='FAQ',query_text='요금제 종류' WHERE consult_request_id=?",
                requestId);
        long secondId = jdbc.queryForObject(
                "INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)"
                        + " VALUES (?,?,2,'FAQ','로밍 신청 방법') RETURNING consult_request_id",
                Long.class, sessionId, inputId);
        var first = consult.prepareTurn(sessionId, requestId, Purpose.GENERAL_FAQ,
                Map.of(), LocationStatus.MISSING);
        var second = consult.prepareTurn(sessionId, secondId, Purpose.GENERAL_FAQ,
                Map.of(), LocationStatus.MISSING);
        var searches = mock(FaqSearchService.class);
        List<String> searched = new ArrayList<>();
        var siblingEvidence = new FaqSearchResponse(91L, null, "PLAN", "요금제 종류",
                "요금제 종류 근거", 0.9, 1, null, 1, null);
        when(searches.search(any(FaqSearchRequest.class))).thenAnswer(invocation -> {
            String query = ((FaqSearchRequest) invocation.getArgument(0)).query();
            searched.add(query);
            return query.equals("로밍 신청 방법") ? List.of() : List.of(siblingEvidence);
        });
        var answers = new FaqSearchAnswerProvider(searches, (input, results) ->
                results.isEmpty()
                        ? ConsultChatProcessingService.GeneratedAnswer.withoutSources(new ChatAnswer(
                                ChatMessage.MessageType.ANSWER,
                                AnswerPromptTemplates.NO_EVIDENCE_ANSWER,
                                ChatMessage.AnswerBasis.NO_EVIDENCE, List.of(), null))
                        : new ConsultChatProcessingService.GeneratedAnswer(new ChatAnswer(
                                ChatMessage.MessageType.ANSWER, "요금제 답변",
                                ChatMessage.AnswerBasis.GROUNDED, List.of(), null),
                                List.of(new AnswerSource(91L, "요금제 종류 근거", 1, null, (short) 1, null))));
        var processor = new ConsultChatProcessingService(
                command -> ConsultChatProcessingService.AnalyzedTurn.multipleFaq(List.of(
                        new ConsultChatProcessingService.FaqTurn(first, "요금제 종류"),
                        new ConsultChatProcessingService.FaqTurn(second, "로밍 신청 방법"))),
                answers, persistence, new ConfirmedConditionConverter(), new RecordingEvents());

        processor.request(processingCommand());

        assertThat(searched).containsExactly("요금제 종류", "로밍 신청 방법");
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("COMPLETED");
        assertThat(states.load(sessionId, requestId).status()).isEqualTo("DONE");
        assertThat(states.load(sessionId, secondId).status()).isEqualTo("DONE");
        String content = jdbc.queryForObject(
                "SELECT content FROM chat_messages WHERE message_id=(SELECT output_message_id"
                        + " FROM chat_executions WHERE execution_id=?)", String.class, executionId);
        assertThat(content).contains("1. 요금제 종류\n요금제 답변",
                "2. 로밍 신청 방법\n" + AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
    }

    @Test
    void routedMultipleFaqQuestionsReachSearchAndOneSavedAnswer() {
        long inputId = jdbc.queryForObject(
                "SELECT input_message_id FROM chat_executions WHERE execution_id=?",
                Long.class, executionId);
        jdbc.update("DELETE FROM consult_requests WHERE consult_request_id=?", requestId);
        String question = "요금제와 로밍 신청 방법 알려줘";
        jdbc.update("UPDATE chat_messages SET content=? WHERE message_id=?", question, inputId);
        when(llmClient.generate(any())).thenReturn("""
                {"intent":"FAQ","confidence":0.97,
                 "refinedQuery":"요금제와 로밍 신청 방법",
                 "extractedConditions":{},"subQueries":[
                   {"order":1,"intent":"FAQ","queryText":"요금제 종류","requestQuote":"요금제","conditions":{}},
                   {"order":2,"intent":"FAQ","queryText":"로밍 신청 방법","requestQuote":"로밍 신청 방법","conditions":{}}]}
                """, """
                {"decision":"MULTIPLE","requestCount":2}
                """, """
                {"unsafe":[false,false]}
                """);
        var context = new FollowupContextService.Context(
                sessionId, inputId, question, List.of());
        var analysis = new QueryRoutingAnalysisProvider(messages, routing,
                ignored -> ConsultTurnAnalysisAdapter.AnalysisResult.rerouteRequest());
        var analyzer = new ConsultTurnAnalysisAdapter(
                ignored -> context, analysis, preparation, new FollowupConditionConverter());
        var searches = mock(FaqSearchService.class);
        List<String> searchQueries = new ArrayList<>();
        when(searches.search(any(FaqSearchRequest.class))).thenAnswer(invocation -> {
            String query = ((FaqSearchRequest) invocation.getArgument(0)).query();
            searchQueries.add(query);
            return List.of(new FaqSearchResponse(null, null, "SERVICE", query,
                    query + " 근거", 0.9, 1, null, 1, null));
        });
        var answers = new FaqSearchAnswerProvider(searches, (input, results) ->
                ConsultChatProcessingService.GeneratedAnswer.withoutSources(new ChatAnswer(
                        ChatMessage.MessageType.ANSWER, results.getFirst().question() + " 답변",
                        ChatMessage.AnswerBasis.GROUNDED, List.of(), null)));
        var events = new RecordingEvents();
        var processor = new ConsultChatProcessingService(
                analyzer, answers, persistence, new ConfirmedConditionConverter(), events);

        processor.request(new ChatProcessingCommand(executionId, sessionId, inputId, question));

        assertThat(searchQueries).containsExactly("요금제", "로밍 신청 방법");
        assertThat(jdbc.queryForList(
                "SELECT status FROM consult_requests WHERE origin_message_id=? ORDER BY subquery_order",
                String.class, inputId)).containsExactly("DONE", "DONE");
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("COMPLETED");
        String content = jdbc.queryForObject(
                "SELECT content FROM chat_messages WHERE message_id=(SELECT output_message_id"
                        + " FROM chat_executions WHERE execution_id=?)", String.class, executionId);
        assertThat(content).contains("1. 요금제\n요금제 답변",
                "2. 로밍 신청 방법\n로밍 신청 방법 답변");
        assertThat(events.sequence).containsExactly("start", "token:" + content,
                "complete:COMPLETED");
    }

    @Test
    void failureToCompleteSecondFaqRollsBackTheCombinedAnswer() {
        long inputId = jdbc.queryForObject(
                "SELECT input_message_id FROM chat_executions WHERE execution_id=?",
                Long.class, executionId);
        jdbc.update("UPDATE consult_requests SET intent='FAQ' WHERE consult_request_id=?", requestId);
        long secondId = jdbc.queryForObject(
                "INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)"
                        + " VALUES (?,?,2,'FAQ','로밍 신청') RETURNING consult_request_id",
                Long.class, sessionId, inputId);
        var first = consult.prepare(sessionId, requestId, Purpose.GENERAL_FAQ,
                Map.of(), LocationStatus.MISSING);
        var second = consult.prepare(sessionId, secondId, Purpose.GENERAL_FAQ,
                Map.of(), LocationStatus.MISSING);
        persistence.persistReadyTurns(executionId, sessionId, List.of(first, second));
        persistence.startAnswer(executionId, sessionId);
        jdbc.update("UPDATE consult_requests SET version=version+1 WHERE consult_request_id=?", secondId);
        var answer = new ChatAnswer(ChatMessage.MessageType.ANSWER, "두 질문의 답변",
                ChatMessage.AnswerBasis.GROUNDED, List.of(), null);

        assertThatThrownBy(() -> persistence.persistFinalAnswers(
                executionId, sessionId,
                List.of(
                        new ConsultChatPersistenceService.ConsultCompletion(
                                requestId, first.expectedVersion() + 1),
                        new ConsultChatPersistenceService.ConsultCompletion(
                                secondId, second.expectedVersion() + 1)),
                answer, List.of())).isInstanceOf(GeneralException.class);

        assertThat(states.load(sessionId, requestId).status()).isEqualTo("PENDING");
        assertThat(states.load(sessionId, secondId).status()).isEqualTo("PENDING");
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("RUNNING");
        assertThat(text("SELECT status FROM chat_messages WHERE message_id=(SELECT output_message_id"
                + " FROM chat_executions WHERE execution_id=?)", executionId))
                .isEqualTo("GENERATING");
    }

    @Test
    void searchFailureStoresFailureBeforeSendingErrorEvent() {
        var prepared =
                consult.prepareTurn(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("강남역")),
                        LocationStatus.MISSING);
        var events = new RecordingEvents();
        var processor =
                new ConsultChatProcessingService(
                        command ->
                                new ConsultChatProcessingService.AnalyzedTurn(
                                        prepared,
                                        null,
                                        Purpose.NEARBY_STORE,
                                        "매장 알려줘",
                                        "매장"),
                        input -> {
                            throw new IllegalStateException("검색 실패");
                        },
                        persistence,
                        new ConfirmedConditionConverter(),
                        events);
        processor.request(processingCommand());
        assertThat(states.load(sessionId, requestId).status()).isEqualTo("PENDING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM chat_messages WHERE session_id=?",
                                Integer.class,
                                sessionId))
                .isEqualTo(2);
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("FAILED");
        assertThat(
                        text(
                                "SELECT status FROM chat_messages WHERE message_id=(SELECT"
                                        + " output_message_id FROM chat_executions WHERE"
                                        + " execution_id=?)",
                                executionId))
                .isEqualTo("FAILED");
        assertThat(events.sequence).containsExactly("start", "error:FAILED");
    }

    @Test
    void disconnectedStreamStoresCancellationBeforeSendingErrorEvent() {
        var prepared =
                consult.prepareTurn(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("강남역")),
                        LocationStatus.MISSING);
        var events = new RecordingEvents();
        var processor =
                new ConsultChatProcessingService(
                        command ->
                                new ConsultChatProcessingService.AnalyzedTurn(
                                        prepared,
                                        null,
                                        Purpose.NEARBY_STORE,
                                        "매장 알려줘",
                                        "매장"),
                        input -> {
                            throw new LlmStreamCancelledException();
                        },
                        persistence,
                        new ConfirmedConditionConverter(),
                        events);

        processor.request(processingCommand());

        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("CANCELLED");
        assertThat(
                        text(
                                "SELECT status FROM chat_messages WHERE message_id=(SELECT"
                                        + " output_message_id FROM chat_executions WHERE"
                                        + " execution_id=?)",
                                executionId))
                .isEqualTo("CANCELLED");
        assertThat(
                        text(
                                "SELECT error_code FROM chat_executions WHERE execution_id=?",
                                executionId))
                .isEqualTo("USER_CANCELLED");
        assertThat(events.sequence).containsExactly("start", "error:CANCELLED");
    }

    private ChatProcessingCommand processingCommand() {
        long input =
                jdbc.queryForObject(
                        "SELECT input_message_id FROM chat_executions WHERE execution_id=?",
                        Long.class,
                        executionId);
        return new ChatProcessingCommand(executionId, sessionId, input, "매장 알려줘");
    }

    @Test
    void declinedLocationSavesAlternativeGuidanceWithoutSearching() {
        createFollowup();
        var prepared =
                consult.prepareTurn(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.declined()),
                        LocationStatus.DECLINED);
        var processor =
                new ConsultChatProcessingService(
                        command ->
                                new ConsultChatProcessingService.AnalyzedTurn(
                                        prepared,
                                        "location",
                                        Purpose.NEARBY_STORE,
                                        "매장 알려줘",
                                        "매장"),
                        input -> {
                            throw new AssertionError("위치 제공 거절 시에는 검색하지 않는다.");
                        },
                        persistence,
                        new ConfirmedConditionConverter());

        processor.request(processingCommand());

        assertThat(states.load(sessionId, requestId).status()).isEqualTo("DONE");
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("COMPLETED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT content FROM chat_messages WHERE session_id=?"
                                        + " AND role='ASSISTANT' AND message_type='ANSWER'",
                                String.class,
                                sessionId))
                .contains("지역");
    }

    @Test
    void followupAnswerIsLinkedWhenAnotherConditionNeedsClarification() {
        long answerMessageId = createFollowup();
        var snapshot = states.load(sessionId, requestId);
        var decision =
                new DialogueDecision(
                        requestId,
                        DialogueDecision.Action.ASK,
                        Map.of(
                                "location", Condition.filled("강남역"),
                                "serviceType", Condition.pending()),
                        "serviceType",
                        "어떤 업무를 찾으세요?",
                        MessageOrigin.TEMPLATE);
        var prepared = new ConsultService.PreparedTurn(sessionId, snapshot.version(), decision);

        persistence.persistClarification(executionId, prepared, "location");

        assertThat(
                        jdbc.queryForObject(
                                "SELECT answered_message_id FROM consult_conditions WHERE"
                                        + " consult_request_id=? AND condition_key='location'",
                                Long.class,
                                requestId))
                .isEqualTo(answerMessageId);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT asked_message_id FROM consult_conditions WHERE"
                                        + " consult_request_id=? AND condition_key='serviceType'",
                                Long.class,
                                requestId))
                .isNotNull();
        assertThat(states.load(sessionId, requestId).status()).isEqualTo("WAITING_CONDITION");
    }

    @Test
    void storeRecommendationCompletesWithoutTextAnswer() {
        createFollowup();
        var prepared =
                consult.prepare(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("강남역")),
                        LocationStatus.MISSING);
        persistence.persistReadyFollowup(executionId, prepared, "location");
        int version = states.load(sessionId, requestId).version();
        var output =
                persistence.persistFinalAnswer(
                        executionId,
                        sessionId,
                        requestId,
                        version,
                        new ChatAnswer(
                                ChatMessage.MessageType.STORE_RESULT,
                                null,
                                null,
                                List.of(),
                                List.of(Map.<String, Object>of("storeId", 1L, "name", "가상 강남점"))));
        assertThat(states.load(sessionId, requestId).status()).isEqualTo("DONE");
        assertThat(
                        text(
                                "SELECT message_type FROM chat_messages WHERE message_id=?",
                                output.outputMessage().messageId()))
                .isEqualTo("STORE_RESULT");
        assertThat(
                        text(
                                "SELECT status FROM chat_messages WHERE message_id=?",
                                output.outputMessage().messageId()))
                .isEqualTo("COMPLETED");
    }

    @Test
    void finalStateConflictRollsBackAnswerAndChatCompletion() {
        createFollowup();
        var prepared =
                consult.prepare(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of("location", Condition.filled("강남역")),
                        LocationStatus.MISSING);
        persistence.persistReadyFollowup(executionId, prepared, "location");
        int version = states.load(sessionId, requestId).version();
        jdbc.update(
                "UPDATE consult_requests SET version=version+1 WHERE consult_request_id=?",
                requestId);
        assertThatThrownBy(
                        () ->
                                persistence.persistFinalAnswer(
                                        executionId,
                                        sessionId,
                                        requestId,
                                        version,
                                        new ChatAnswer(
                                                ChatMessage.MessageType.ANSWER,
                                                "안내 답변",
                                                null,
                                                List.of(),
                                                null)))
                .isInstanceOf(GeneralException.class);
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("RUNNING");
        assertThat(text("SELECT status FROM chat_sessions WHERE session_id=?", sessionId))
                .isEqualTo("NEED_CLARIFICATION");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM chat_messages WHERE session_id=?",
                                Integer.class,
                                sessionId))
                .isEqualTo(3);
        assertThat(states.load(sessionId, requestId).status()).isEqualTo("PENDING");
    }

    @Test
    void cannotFinishBeforeWaitingConditionIsResolved() {
        createFollowup();
        int version = states.load(sessionId, requestId).version();
        assertThatThrownBy(
                        () ->
                                persistence.persistFinalAnswer(
                                        executionId,
                                        sessionId,
                                        requestId,
                                        version,
                                        new ChatAnswer(
                                                ChatMessage.MessageType.ANSWER,
                                                "안내 답변",
                                                null,
                                                List.of(),
                                                null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(text("SELECT status FROM chat_executions WHERE execution_id=?", executionId))
                .isEqualTo("RUNNING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM chat_messages WHERE session_id=?",
                                Integer.class,
                                sessionId))
                .isEqualTo(3);
        assertThat(states.load(sessionId, requestId).status()).isEqualTo("WAITING_CONDITION");
    }

    private long createFollowup() {
        var question =
                consult.prepare(
                        sessionId,
                        requestId,
                        Purpose.NEARBY_STORE,
                        Map.of(),
                        LocationStatus.MISSING);
        persistence.persistClarification(executionId, question);
        long inputId =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " chat_messages(session_id,sequence_no,role,message_type,content,status,completed_at)"
                            + " VALUES (?,3,'USER','QUESTION','강남역이요','COMPLETED',now()) RETURNING"
                            + " message_id",
                        Long.class,
                        sessionId);
        executionId =
                jdbc.queryForObject(
                        "INSERT INTO chat_executions(session_id,input_message_id,status) VALUES"
                                + " (?,?,'RUNNING') RETURNING execution_id",
                        Long.class,
                        sessionId,
                        inputId);
        return inputId;
    }

    private String text(String sql, long id) {
        return jdbc.queryForObject(sql, String.class, id);
    }

    private final class RecordingEvents implements ConsultChatEvents {
        private final List<String> sequence = new ArrayList<>();

        @Override
        public void started(ChatExecutionState state) {
            sequence.add("start");
        }

        @Override
        public LlmStreamHandler stream(long targetExecutionId) {
            return new LlmStreamHandler() {
                @Override
                public void onToken(String token) {
                    sequence.add("token:" + token);
                }

                @Override
                public void onComplete() {
                    sequence.add("stream-complete");
                }

                @Override
                public void onError(Throwable error) {
                    sequence.add("stream-error");
                }
            };
        }

        @Override
        public void completed(long targetExecutionId, ChatExecutionState state) {
            sequence.add(
                    "complete:"
                            + text(
                                    "SELECT status FROM chat_executions WHERE execution_id=?",
                                    targetExecutionId));
        }

        @Override
        public void failed(long targetExecutionId, ChatFailure failure) {
            sequence.add(
                    "error:"
                            + text(
                                    "SELECT status FROM chat_executions WHERE execution_id=?",
                                    targetExecutionId));
        }
    }
}
