package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.dto.res.ChatMessageHistoryItemResponse;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ChatProcessingPort;
import com.telme.chat.service.ChatSessionService;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnalyzedTurn;
import com.telme.consult.service.ConsultChatProcessingService.TurnAnalyzer;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.faq.exception.FaqErrorCode;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.service.AnswerPromptTemplates;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 검색·모델만 대체하고 실제 처리기, Guard, 트랜잭션, SSE 및 이력 조회 API를 연결한다. */
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "telme.consult.persistence-enabled=true",
        "telme.consult.chat-integration-enabled=true",
        "telme.consult.rag-integration-enabled=true",
        "telme.consult.llm-enabled=false",
        "llm.provider=ollama",
        "llm.model=guard-delivery-test",
        "llm.retry.wait-duration=0ms",
        "rag.evidence-check.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=2"
})
class ConsultGuardedAnswerDeliveryIntegrationTest {
    private static final String SUPPORTED = "재발급 비용은 7,700원입니다.";
    private static final String UNSUPPORTED = " 택배비는 이 금액에 포함됩니다.";
    private static final String QUERY = "유심 재발급 비용과 배송비를 알려주세요";

    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired ChatProcessingPort processor;
    @Autowired ConsultService consult;
    @Autowired ChatSessionService sessions;
    @Autowired ChatEmitterRegistry emitters;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean TurnAnalyzer analyzer;
    @MockitoBean FaqSearchService search;
    @MockitoBean(name = "baseLlmClient", enforceOverride = true) LlmClient model;
    private long userId;
    private long sessionId;
    private long executionId;
    private long requestId;
    private ChatProcessingCommand command;
    private CaptureEmitter emitter;
    private Consumer<LlmStreamHandler> script;

    @BeforeEach
    void setup() {
        userId = jdbc.queryForObject(
                "INSERT INTO users(email,name) VALUES (?,'Guard 전달 테스트') RETURNING user_id",
                Long.class, "guard-delivery-" + UUID.randomUUID() + "@example.com");
        sessionId = jdbc.queryForObject(
                "INSERT INTO chat_sessions(user_id,title) VALUES (?,'Guard 검증') RETURNING session_id",
                Long.class, userId);
        long inputId = jdbc.queryForObject(
                "INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,status,completed_at)"
                        + " VALUES (?,1,'USER','QUESTION',?,'COMPLETED',now()) RETURNING message_id",
                Long.class, sessionId, QUERY);
        executionId = jdbc.queryForObject(
                "INSERT INTO chat_executions(session_id,input_message_id,status)"
                        + " VALUES (?,?,'RUNNING') RETURNING execution_id",
                Long.class, sessionId, inputId);
        requestId = jdbc.queryForObject(
                "INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)"
                        + " VALUES (?,?,1,'FAQ',?) RETURNING consult_request_id",
                Long.class, sessionId, inputId, QUERY);
        var prepared = consult.prepareTurn(sessionId, requestId, Purpose.GENERAL_FAQ,
                Map.of(), LocationStatus.MISSING);
        command = new ChatProcessingCommand(executionId, sessionId, inputId, QUERY);
        when(analyzer.analyze(command)).thenReturn(new AnalyzedTurn(prepared, null,
                Purpose.GENERAL_FAQ, QUERY, QUERY));
        when(search.search(any())).thenReturn(List.of(new FaqSearchResponse(
                9999999L, null, "USIM", "재발급 비용은 얼마인가요?",
                "재발급 비용은 7,700원이며 택배로 2~3 영업일이 걸립니다.",
                0.9, 1, LocalDate.of(2026, 9, 17), 1, null)));
        doAnswer(invocation -> {
            LlmStreamHandler stream = invocation.getArgument(1);
            script.accept(stream);
            return null;
        }).when(model).stream(any(), any());
        emitter = new CaptureEmitter();
        emitters.register(executionId, emitter);
    }

    @AfterEach
    void cleanup() {
        emitter.disconnect();
        jdbc.update("DELETE FROM consult_conditions WHERE consult_request_id IN"
                + " (SELECT consult_request_id FROM consult_requests WHERE session_id=?)", sessionId);
        jdbc.update("DELETE FROM consult_requests WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM chat_sessions WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM users WHERE user_id=?", userId);
    }

    @Test
    void activeProcessorSendsNormalAnswerOnceAfterCommitAndHistoryKeepsIt() {
        assertThat(context.getBeansOfType(ChatProcessingPort.class)).hasSize(1);
        assertThat(processor).isInstanceOf(ConsultChatProcessingService.class);
        script = stream -> {
            stream.onToken("재발급 비용은 ");
            assertThat(emitter.tokens()).isEmpty();
            stream.onToken("7,700원입니다.");
            assertThat(emitter.tokens()).isEmpty();
            stream.onComplete();
            assertThat(emitter.tokens()).isEmpty();
        };

        processor.request(command);

        assertCompleted(SUPPORTED, "GROUNDED");
        assertThat(historyAnswer().content()).isEqualTo(SUPPORTED);
        assertThat(sessions.getMessages(new ChatActor(userId, null), sessionId, null, 20)
                .runningExecutionId()).isNull();
    }

    @Test
    void modifiedAnswerDoesNotExposeRemovedPolicy() {
        script = success(SUPPORTED + UNSUPPORTED);
        processor.request(command);

        assertCompleted(SUPPORTED, "GROUNDED");
        assertThat(emitter.tokens()).noneMatch(token -> token.contains("포함"));
    }

    @Test
    void entirePolicyRemovalSendsAndStoresSafeGuidance() {
        script = success("택배비는 이 금액에 포함됩니다.");
        processor.request(command);

        assertCompleted(AnswerPromptTemplates.NO_EVIDENCE_ANSWER, "NO_EVIDENCE");
    }

    @Test
    void guardExceptionSendsSafeGuidanceAndPreservesFailureRecord() {
        script = success("재발급 비용은 84,700원입니다.");
        processor.request(command);

        assertCompleted(AnswerPromptTemplates.NO_EVIDENCE_ANSWER, "NO_EVIDENCE");
        assertThat(jdbc.queryForList("SELECT status FROM llm_generations WHERE execution_id=?",
                String.class, executionId)).containsExactly("MODEL_ERROR");
        assertThat(emitter.tokens()).noneMatch(token -> token.contains("84,700"));
    }

    @Test
    void noSearchResultSendsAndStoresSameFixedGuidanceWithoutCallingModel() {
        when(search.search(any())).thenReturn(List.of());
        processor.request(command);

        verify(model, never()).stream(any(), any());
        assertThat(emitter.tokens()).containsExactly(historyAnswer().content());
        assertThat(historyAnswer().answerBasis()).isEqualTo("NO_EVIDENCE");
        assertThat(emitter.names()).containsExactly("start", "token", "complete");
        assertThat(historyAnswer().content()).isNotBlank();
        assertCompletedHistory(historyAnswer().content(), "NO_EVIDENCE");
        assertThat(jdbc.queryForList("SELECT status FROM llm_generations WHERE execution_id=?",
                String.class, executionId)).allMatch("NO_EVIDENCE"::equals);
    }

    @ParameterizedTest
    @MethodSource("failures")
    void modelFailureOrTimeoutDoesNotExposePartialAnswer(LlmErrorCode code) {
        script = stream -> {
            stream.onToken(SUPPORTED + UNSUPPORTED);
            assertThat(emitter.tokens()).isEmpty();
            stream.onError(new GeneralException(code));
        };
        processor.request(command);

        assertThat(emitter.tokens()).isEmpty();
        assertThat(emitter.names()).containsExactly("start", "error");
        assertThat(executionStatus()).isEqualTo("FAILED");
        assertThat(outputContent()).isNull();
        assertFailedHistory("FAILED", code.getCode());
    }

    static Stream<LlmErrorCode> failures() {
        return Stream.of(LlmErrorCode.MODEL_ERROR, LlmErrorCode.TIMEOUT);
    }

    @Test
    void realRetryChainSendsRetryStatusThenOnlyFinalAnswer() {
        var attempt = new AtomicInteger();
        script = stream -> {
            if (attempt.incrementAndGet() == 1) {
                stream.onError(new GeneralException(LlmErrorCode.TIMEOUT));
                return;
            }
            stream.onToken(SUPPORTED);
            stream.onComplete();
        };
        processor.request(command);

        assertThat(attempt.get()).isEqualTo(2);
        assertCompleted(SUPPORTED, "GROUNDED", "start", "status", "token", "complete");
        assertThat(emitter.events.get(1).data()).isEqualTo("RETRYING");
        assertThat(jdbc.queryForList(
                "SELECT status FROM llm_generations WHERE execution_id=? ORDER BY attempt",
                String.class, executionId)).containsExactly("TIMEOUT", "SUCCESS");
    }

    @Test
    void disconnectedSubscriberCancelsBufferedGenerationWithoutAnswerTokens() {
        script = stream -> {
            stream.onToken(SUPPORTED);
            emitter.disconnect();
            try {
                stream.onToken(UNSUPPORTED);
                throw new AssertionError("연결 이탈 후 생성이 계속됨");
            } catch (LlmStreamCancelledException cancelled) {
                stream.onError(cancelled);
            }
        };
        processor.request(command);

        assertThat(emitter.tokens()).isEmpty();
        assertThat(executionStatus()).isEqualTo("CANCELLED");
        assertThat(outputContent()).isNull();
        assertThat(jdbc.queryForList("SELECT status FROM llm_generations WHERE execution_id=?",
                String.class, executionId)).containsExactly("CANCELLED");
        assertFailedHistory("CANCELLED", "USER_CANCELLED");
    }

    @Test
    void persistenceRollbackDoesNotSendEvenTheValidatedAnswer() {
        script = stream -> {
            stream.onToken(SUPPORTED);
            jdbc.update("UPDATE consult_requests SET version=version+1 WHERE consult_request_id=?",
                    requestId);
            stream.onComplete();
        };
        processor.request(command);

        assertThat(emitter.tokens()).isEmpty();
        assertThat(emitter.names()).containsExactly("start", "error");
        assertThat(executionStatus()).isEqualTo("FAILED");
        assertThat(outputContent()).isNull();
        assertFailedHistory("FAILED", "CONSULT409-0");
    }

    @Test
    void deliveryFailureAfterCommitKeepsCompletedAnswerForReconnect() {
        emitter.failTokenDelivery = true;
        script = success(SUPPORTED);
        processor.request(command);

        assertThat(emitter.tokens()).isEmpty();
        assertThat(executionStatus()).isEqualTo("COMPLETED");
        assertThat(historyAnswer().content()).isEqualTo(SUPPORTED);
        assertCompletedHistory(SUPPORTED, "GROUNDED");
    }

    @Test
    void historyApiNeverReturnsRawAnswerWhileGenerationAndGuardArePending() {
        script = stream -> {
            stream.onToken(SUPPORTED + UNSUPPORTED);
            JsonNode pending = historyFromApi("?size=20");
            assertThat(pending.path("runningExecutionId").asLong()).isEqualTo(executionId);
            JsonNode output = pending.path("messages").get(1);
            assertThat(output.path("status").asText()).isEqualTo("GENERATING");
            assertThat(output.path("content").isNull()).isTrue();
            assertThat(outputContent()).isNull();
            stream.onComplete();
            // Guard 검사는 끝났어도 저장 경계에 도달하기 전에는 원문·최종문을 조회할 수 없다.
            assertThat(historyFromApi("?size=20").path("messages").get(1)
                    .path("content").isNull()).isTrue();
        };
        processor.request(command);

        assertCompleted(SUPPORTED, "GROUNDED");
    }

    @Test
    void partialAttemptFailureDoesNotRetryOrStoreAbandonedText() {
        var attempts = new AtomicInteger();
        script = stream -> {
            attempts.incrementAndGet();
            stream.onToken("재발급 비용은 84,700원입니다.");
            assertThat(outputContent()).isNull();
            stream.onError(new GeneralException(LlmErrorCode.TIMEOUT));
        };
        processor.request(command);

        // 실제 재시도 체인은 원문 토큰 수신 후 실패를 재시도하지 않는다.
        assertThat(attempts.get()).isEqualTo(1);
        assertThat(emitter.tokens()).isEmpty();
        assertFailedHistory("FAILED", LlmErrorCode.TIMEOUT.getCode());
        assertThat(historyFromApi("?size=20").toString()).doesNotContain("84,700");
    }

    @Test
    void duplicateCompletionCallbackDoesNotAppendOrDuplicateFinalAnswer() {
        script = stream -> {
            stream.onToken(SUPPORTED);
            stream.onComplete();
            stream.onComplete();
        };
        processor.request(command);

        assertCompleted(SUPPORTED, "GROUNDED");
    }

    @Test
    void pagedHistoryReturnsSameFinalAnswerAndEarlierQuestionSeparately() {
        script = success(SUPPORTED + UNSUPPORTED);
        processor.request(command);

        assertCompleted(SUPPORTED, "GROUNDED");
        JsonNode latest = historyFromApi("?size=1");
        assertThat(latest.path("messages").size()).isEqualTo(1);
        assertThat(latest.path("messages").get(0).path("content").asText()).isEqualTo(SUPPORTED);
        assertThat(latest.path("hasOlderMessages").asBoolean()).isTrue();
        JsonNode older = historyFromApi("?size=1&beforeSequenceNo="
                + latest.path("nextBeforeSequenceNo").asInt());
        assertThat(older.path("messages").size()).isEqualTo(1);
        assertThat(older.path("messages").get(0).path("role").asText()).isEqualTo("USER");
        assertThat(older.path("messages").get(0).path("content").asText()).isEqualTo(QUERY);
        assertThat(older.path("hasOlderMessages").asBoolean()).isFalse();
    }

    @Test
    void reconnectAfterGuardedCompletionReturnsSameStoredMessageIdWithoutNewAnswer() {
        script = success(SUPPORTED + UNSUPPORTED);
        processor.request(command);
        assertCompleted(SUPPORTED, "GROUNDED");

        String replay = terminalReplay();
        assertThat(replay).contains("event:complete", "\"status\":\"COMPLETED\"",
                "\"messageId\":" + historyAnswer().messageId());
        assertThat(replay).doesNotContain("event:token", "event:error", "포함");
        assertThat(emitters.isRegistered(executionId)).isFalse();
        assertCompletedHistory(SUPPORTED, "GROUNDED");
    }

    @Test
    void reconnectAfterPartialTimeoutReturnsErrorAndHistoryStillHasNoAnswerText() {
        script = stream -> {
            stream.onToken(SUPPORTED + UNSUPPORTED);
            stream.onError(new GeneralException(LlmErrorCode.TIMEOUT));
        };
        processor.request(command);

        String replay = terminalReplay();
        assertThat(replay).contains("event:error", "\"status\":\"FAILED\"",
                LlmErrorCode.TIMEOUT.getCode());
        assertThat(replay).doesNotContain("event:complete", "event:token", SUPPORTED);
        assertThat(emitters.isRegistered(executionId)).isFalse();
        assertFailedHistory("FAILED", LlmErrorCode.TIMEOUT.getCode());
    }

    @ParameterizedTest
    @EnumSource(value = LlmErrorCode.class, names = {"MODEL_ERROR", "TIMEOUT", "CONNECTION_FAILED"})
    void exhaustedModelFailureTerminatesOnceWithReadableGuidance(LlmErrorCode code) {
        var attempts = new AtomicInteger();
        script = stream -> {
            attempts.incrementAndGet();
            stream.onError(new GeneralException(code));
        };
        processor.request(command);

        int expectedAttempts = code == LlmErrorCode.MODEL_ERROR ? 1 : 2;
        assertThat(attempts.get()).isEqualTo(expectedAttempts);
        assertThat(emitter.tokens()).isEmpty();
        assertThat(emitter.names()).endsWith("error").doesNotContain("complete");
        assertThat(emitter.names().stream().filter("error"::equals).count()).isEqualTo(1);
        assertFailedHistory("FAILED", code.getCode());
        assertThat(jdbc.queryForList(
                "SELECT status FROM llm_generations WHERE execution_id=? ORDER BY attempt",
                String.class, executionId)).hasSize(expectedAttempts)
                .allMatch(status -> status.equals(code.name()));
        assertReadableFailureGuidance();
    }

    @Test
    void synchronousTimeoutBeforeFirstTokenUsesSameRetryAndRecordingPath() {
        var attempts = new AtomicInteger();
        script = stream -> {
            if (attempts.incrementAndGet() == 1) {
                throw new GeneralException(LlmErrorCode.TIMEOUT);
            }
            stream.onToken(SUPPORTED);
            stream.onComplete();
        };
        processor.request(command);

        assertThat(attempts.get()).isEqualTo(2);
        assertCompleted(SUPPORTED, "GROUNDED", "start", "status", "token", "complete");
        assertThat(jdbc.queryForList(
                "SELECT status FROM llm_generations WHERE execution_id=? ORDER BY attempt",
                String.class, executionId)).containsExactly("TIMEOUT", "SUCCESS");
    }

    @ParameterizedTest
    @MethodSource("searchFailures")
    void searchSystemFailureIsDistinctFromNoEvidenceAndDoesNotCallModel(RuntimeException failure) {
        when(search.search(any())).thenThrow(failure);
        processor.request(command);

        verify(model, never()).stream(any(), any());
        assertThat(emitter.tokens()).isEmpty();
        assertThat(emitter.names()).containsExactly("start", "error");
        assertFailedHistory("FAILED", "FAQ_SEARCH_FAILED");
        assertThat(jdbc.queryForList("SELECT status FROM llm_generations WHERE execution_id=?",
                String.class, executionId)).isEmpty();
        assertReadableFailureGuidance();
    }

    static Stream<RuntimeException> searchFailures() {
        return Stream.of(new DataAccessResourceFailureException("internal-search-detail"),
                new GeneralException(FaqErrorCode.EMBEDDING_REQUEST_FAILED));
    }

    @Test
    void refinedSearchFailureAfterEmptyOriginalSearchIsNotReportedAsNoEvidence() {
        var prepared = consult.prepareTurn(sessionId, requestId, Purpose.GENERAL_FAQ,
                Map.of(), LocationStatus.MISSING);
        when(analyzer.analyze(command)).thenReturn(new AnalyzedTurn(prepared, null,
                Purpose.GENERAL_FAQ, QUERY, "유심 배송비 포함 여부"));
        when(search.search(any())).thenReturn(List.of())
                .thenThrow(new IllegalStateException("internal-search-detail"));
        processor.request(command);

        verify(search, org.mockito.Mockito.times(2)).search(any());
        verify(model, never()).stream(any(), any());
        assertFailedHistory("FAILED", "FAQ_SEARCH_FAILED");
        assertReadableFailureGuidance();
    }

    @Test
    void missingSearchResponseIsASystemFailureRatherThanEmptyEvidence() {
        when(search.search(any())).thenReturn(null);
        processor.request(command);

        verify(model, never()).stream(any(), any());
        assertThat(emitter.tokens()).isEmpty();
        assertFailedHistory("FAILED", "FAQ_SEARCH_FAILED");
        assertReadableFailureGuidance();
    }

    @Test
    void finalRetryFailureDiscardsPartialAnswerAndKeepsOneFailedHistoryRow() {
        var attempts = new AtomicInteger();
        script = stream -> {
            if (attempts.incrementAndGet() == 1) {
                stream.onError(new GeneralException(LlmErrorCode.TIMEOUT));
                return;
            }
            stream.onToken(SUPPORTED + UNSUPPORTED);
            stream.onError(new GeneralException(LlmErrorCode.CONNECTION_FAILED));
        };
        processor.request(command);

        assertThat(attempts.get()).isEqualTo(2);
        assertThat(emitter.names()).containsExactly("start", "status", "error");
        assertThat(emitter.tokens()).isEmpty();
        assertFailedHistory("FAILED", LlmErrorCode.CONNECTION_FAILED.getCode());
        assertReadableFailureGuidance();
        assertThat(jdbc.queryForList(
                "SELECT status FROM llm_generations WHERE execution_id=? ORDER BY attempt",
                String.class, executionId)).containsExactly("TIMEOUT", "CONNECTION_FAILED");
    }

    private void assertReadableFailureGuidance() {
        JsonNode payload = objectMapper.valueToTree(emitter.events.getLast().data());
        assertThat(payload.path("message").asText()).isNotBlank();
        String expectedTopic = switch (payload.path("errorCode").asText()) {
            case "FAQ_SEARCH_FAILED" -> "검색";
            case "LLM503-0" -> "연결";
            case "LLM504-0" -> "시간";
            default -> "생성";
        };
        assertThat(payload.path("message").asText()).contains(expectedTopic);
        assertThat(payload.toString()).doesNotContain("internal-search-detail");
        String replay = terminalReplay();
        assertThat(replay).contains("event:error", "\"message\":");
        assertThat(replay).doesNotContain("event:complete", "internal-search-detail");
    }

    private String terminalReplay() {
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        try {
            var subscription = mockMvc.perform(get(
                            "/api/v1/chat/sessions/{sessionId}/executions/{executionId}/subscribe",
                            sessionId, executionId).session(httpSession))
                    .andExpect(request().asyncStarted()).andReturn();
            return mockMvc.perform(asyncDispatch(subscription)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new AssertionError("종료된 실행의 재구독 검증 실패", exception);
        }
    }

    private Consumer<LlmStreamHandler> success(String answer) {
        return stream -> {
            stream.onToken(answer);
            assertThat(emitter.tokens()).isEmpty();
            stream.onComplete();
            assertThat(emitter.tokens()).isEmpty();
        };
    }

    private void assertCompleted(String answer, String basis, String... names) {
        assertThat(executionStatus()).isEqualTo("COMPLETED");
        assertThat(outputContent()).isEqualTo(answer);
        assertThat(emitter.tokens()).containsExactly(answer);
        assertThat(emitter.names()).containsExactly(names.length == 0
                ? new String[] {"start", "token", "complete"} : names);
        assertThat(historyAnswer().content()).isEqualTo(answer);
        assertThat(historyAnswer().answerBasis()).isEqualTo(basis);
        var terminal = emitter.events.getLast().data();
        assertThat(terminal).isInstanceOf(ChatExecutionState.class);
        assertThat(((ChatExecutionState) terminal).outputMessage().messageId())
                .isEqualTo(historyAnswer().messageId());
        assertCompletedHistory(answer, basis);
    }

    private void assertCompletedHistory(String answer, String basis) {
        JsonNode firstRead = historyFromApi("?size=20");
        assertThat(firstRead.path("messages").size()).isEqualTo(2);
        assertThat(firstRead.path("runningExecutionId").isNull()).isTrue();
        JsonNode output = firstRead.path("messages").get(1);
        assertThat(output.path("messageId").asLong()).isEqualTo(historyAnswer().messageId());
        assertThat(output.path("role").asText()).isEqualTo("ASSISTANT");
        assertThat(output.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(output.path("content").asText()).isEqualTo(answer).isEqualTo(outputContent());
        assertThat(output.path("answerBasis").asText()).isEqualTo(basis);
        assertThat(output.path("completedAt").isNull()).isFalse();
        assertOneAssistantMessage();
        // SSE 종료 후 새 HTTP 요청으로 재조회한다. 브라우저 새로고침을 검증한 것은 아니다.
        emitter.disconnect();
        assertThat(historyFromApi("?size=20")).isEqualTo(firstRead);
    }

    private void assertFailedHistory(String expectedStatus, String errorCode) {
        JsonNode firstRead = historyFromApi("?size=20");
        assertThat(firstRead.path("messages").size()).isEqualTo(2);
        assertThat(firstRead.path("runningExecutionId").isNull()).isTrue();
        JsonNode output = firstRead.path("messages").get(1);
        assertThat(output.path("role").asText()).isEqualTo("ASSISTANT");
        assertThat(output.path("status").asText()).isEqualTo(expectedStatus);
        assertThat(output.path("content").isNull()).isTrue();
        assertThat(output.path("answerBasis").isNull()).isTrue();
        assertThat(output.path("completedAt").isNull()).isFalse();
        var execution = sessions.getExecution(new ChatActor(userId, null), executionId);
        assertThat(execution.status().name()).isEqualTo(expectedStatus);
        assertThat(execution.errorCode()).isEqualTo(errorCode);
        assertThat(execution.outputMessage().messageId()).isEqualTo(output.path("messageId").asLong());
        assertOneAssistantMessage();
        assertThat(historyFromApi("?size=20")).isEqualTo(firstRead);
    }

    private void assertOneAssistantMessage() {
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM chat_messages WHERE session_id=? AND role='ASSISTANT'",
                Integer.class, sessionId)).isEqualTo(1);
    }

    private JsonNode historyFromApi(String query) {
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        try {
            var response = mockMvc.perform(get("/api/v1/chat/sessions/" + sessionId + "/messages" + query)
                            .session(httpSession))
                    .andExpect(status().isOk()).andReturn().getResponse();
            return objectMapper.readTree(response.getContentAsByteArray()).path("result");
        } catch (Exception exception) {
            throw new AssertionError("대화 기록 조회 API 검증 실패", exception);
        }
    }

    private ChatMessageHistoryItemResponse historyAnswer() {
        return sessions.getMessages(new ChatActor(userId, null), sessionId, null, 20)
                .messages().getLast();
    }

    private String executionStatus() {
        return jdbc.queryForObject("SELECT status FROM chat_executions WHERE execution_id=?",
                String.class, executionId);
    }

    private String outputContent() {
        return jdbc.queryForObject("SELECT content FROM chat_messages WHERE message_id="
                        + "(SELECT output_message_id FROM chat_executions WHERE execution_id=?)",
                String.class, executionId);
    }

    private record Event(String name, Object data) {}

    private final class CaptureEmitter extends SseEmitter {
        private final List<Event> events = new ArrayList<>();
        private Runnable disconnected;
        private boolean failTokenDelivery;

        @Override
        public void onCompletion(Runnable callback) { disconnected = callback; }

        @Override
        public void complete() {}

        void disconnect() { if (disconnected != null) disconnected.run(); }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            String name = null;
            Object payload = null;
            for (var item : builder.build()) {
                Object data = item.getData();
                if (data instanceof String text && text.startsWith("event:")) {
                    name = text.substring(6, text.indexOf('\n'));
                } else if (!(data instanceof String text && text.isBlank())) {
                    payload = data;
                }
            }
            if ("token".equals(name)) {
                // 외부 전송 시점에 별도 DB 조회로 커밋된 최종 문자열과 일치하는지 확인한다.
                assertThat(executionStatus()).isEqualTo("COMPLETED");
                assertThat(payload).isEqualTo(outputContent());
                if (failTokenDelivery) throw new IOException("연결 이탈");
            }
            events.add(new Event(name, payload));
        }

        List<String> names() { return events.stream().map(Event::name).toList(); }
        List<String> tokens() {
            return events.stream().filter(event -> event.name().equals("token"))
                    .map(event -> (String) event.data()).toList();
        }
    }
}
