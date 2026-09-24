package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatFailure;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.MessageOrigin;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.repository.JdbcConsultStateStore;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.dto.res.AnswerResult.AnswerSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

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
    long userId;
    long sessionId;
    long executionId;
    long requestId;

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
                            var stream = events.stream(input.executionId());
                            stream.onToken("매장");
                            stream.onComplete();
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
                        "start", "token:매장", "stream-complete", "complete:COMPLETED");
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
