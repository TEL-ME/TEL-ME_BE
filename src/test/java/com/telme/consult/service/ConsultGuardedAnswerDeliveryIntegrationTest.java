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
import com.telme.chat.service.ChatExecutionTraceService;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
    @Autowired ChatExecutionTraceService traces;
    @Autowired com.telme.rag.service.AnswerGenerator answerGenerator;
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
        assertThat(traceFromApi().at("/steps/guard/outcome").asText()).isEqualTo("MODIFIED");
    }

    @Test
    void entirePolicyRemovalSendsAndStoresSafeGuidance() {
        script = success("택배비는 이 금액에 포함됩니다.");
        processor.request(command);

        assertCompleted(AnswerPromptTemplates.NO_EVIDENCE_ANSWER, "NO_EVIDENCE");
        assertThat(traceFromApi().at("/steps/guard/outcome").asText()).isEqualTo("REPLACED");
    }

    @Test
    void guardExceptionSendsSafeGuidanceAndPreservesFailureRecord() {
        script = success("재발급 비용은 84,700원입니다.");
        processor.request(command);

        assertCompleted(AnswerPromptTemplates.NO_EVIDENCE_ANSWER, "NO_EVIDENCE");
        assertThat(jdbc.queryForList("SELECT status FROM llm_generations WHERE execution_id=?",
                String.class, executionId)).containsExactly("MODEL_ERROR");
        assertThat(emitter.tokens()).noneMatch(token -> token.contains("84,700"));
        assertThat(traceFromApi().at("/steps/guard/reason").asText()).isEqualTo("GUARD_EXCEPTION");
        assertThat(traceFromApi().path("steps").toString()).doesNotContain("84,700");
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
        assertThat(traceFromApi().at("/steps/searchResults/0/status").asText()).isEqualTo("EMPTY");
        assertThat(traceFromApi().at("/steps/guard/outcome").asText()).isEqualTo("NOT_RUN");
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
        var trace = traceFromApi();
        assertThat(trace.path("modelAttempts")).hasSize(2);
        assertThat(trace.at("/modelAttempts/1/configuration/options/temperature").asDouble()).isZero();
        assertThat(trace.at("/modelAttempts/1/configuration/options/num_predict").asInt()).isEqualTo(1024);
        assertThat(trace.at("/modelAttempts/1/configuration/options/num_ctx").asInt()).isEqualTo(8192);
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
        assertThat(traceFromApi().at("/steps/finalTransmission/status").asText()).isEqualTo("DISPATCH_ERROR");
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
        assertThat(traceFromApi().at("/steps/searchResults/0/status").asText()).isEqualTo("FOUND");
        assertThat(traceFromApi().at("/steps/guard/reason").asText()).isEqualTo("GENERATION_FAILED");
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
        assertThat(traceFromApi().at("/steps/searchResults/0/status").asText()).isEqualTo("ERROR");
        assertThat(traceFromApi().path("steps").has("generationInput")).isFalse();
        assertThat(traceFromApi().path("steps").toString()).doesNotContain("internal-search-detail");
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

    @Test
    void rewrittenSearchKeepsBothRequestsAndExactFaqSnapshotAfterFaqEdit() {
        String refined = "유심 재발급 요금";
        var prepared = consult.prepareTurn(sessionId, requestId, Purpose.GENERAL_FAQ,
                Map.of(), LocationStatus.MISSING);
        when(analyzer.analyze(command)).thenReturn(new AnalyzedTurn(prepared, null,
                Purpose.GENERAL_FAQ, QUERY, refined));
        long faqId = jdbc.queryForObject(
                "INSERT INTO faqs(category,question,answer) VALUES ('USIM',?,?) RETURNING faq_id",
                Long.class, "재발급 비용 질문", "재발급 비용은 7,700원입니다.");
        try {
            when(search.search(any())).thenAnswer(invocation -> {
                var request = (com.telme.faq.dto.req.FaqSearchRequest) invocation.getArgument(0);
                return request.query().equals(QUERY) ? List.of() : List.of(new FaqSearchResponse(
                        faqId, "TRACE-FAQ", "USIM", "재발급 비용 질문",
                        "재발급 비용은 7,700원입니다.", 0.876543, 1,
                        LocalDate.of(2026, 10, 1), 1, "QUESTION_ONLY"));
            });
            script = success(SUPPORTED);
            processor.request(command);
            jdbc.update("UPDATE faqs SET answer='이후 변경된 답변', version=2 WHERE faq_id=?", faqId);

            var trace = traceFromApi();
            assertThat(trace.at("/steps/analysis/refinedQuery").asText()).isEqualTo(refined);
            assertThat(trace.at("/steps/searchRequests")).hasSize(2);
            assertThat(trace.at("/steps/searchRequests/0/query").asText()).isEqualTo(QUERY);
            assertThat(trace.at("/steps/searchRequests/1/query").asText()).isEqualTo(refined);
            assertThat(trace.at("/steps/searchResults/0/status").asText()).isEqualTo("EMPTY");
            assertThat(trace.at("/steps/searchResults/1/status").asText()).isEqualTo("FOUND");
            assertThat(trace.at("/steps/generationInput/sources/0/faqId").asLong()).isEqualTo(faqId);
            assertThat(trace.at("/steps/generationInput/sources/0/answer").asText()).isEqualTo(SUPPORTED);
            assertThat(trace.at("/steps/generationInput/sources/0/score").asDouble()).isEqualTo(0.876543);
            assertThat(trace.at("/steps/generationInput/sources/0/searchRank").asInt()).isEqualTo(1);
            assertThat(trace.at("/steps/generationInput/sources/0/version").asInt()).isEqualTo(1);
            assertThat(trace.at("/steps/generationInput/userQuery").asText()).isEqualTo(QUERY);
            assertThat(trace.at("/steps/guard/outcome").asText()).isEqualTo("KEPT");
            assertCompleted(SUPPORTED, "GROUNDED");
        } finally {
            jdbc.update("DELETE FROM message_sources WHERE faq_id=?", faqId);
            jdbc.update("DELETE FROM faqs WHERE faq_id=?", faqId);
        }
    }

    @Test
    void existingRoutingIsJoinedUsingInputMessageAndOldExecutionIsNotInvented() {
        jdbc.update("INSERT INTO query_routings(message_id,intent,refined_query,confidence,method)"
                        + " VALUES (?,'FAQ','기존 정제 질문',0.875,'LLM')", command.inputMessageId());
        var trace = traceFromApi();
        assertThat(trace.path("traceRecorded").asBoolean()).isFalse();
        assertThat(trace.path("steps").isNull()).isTrue();
        assertThat(trace.at("/routing/0/intent").asText()).isEqualTo("FAQ");
        assertThat(trace.at("/routing/0/refinedQuery").asText()).isEqualTo("기존 정제 질문");
        assertThat(trace.at("/routing/0/confidence").asDouble()).isEqualTo(0.875);
        assertThat(trace.path("finalAnswer").isNull()).isTrue();
    }

    @Test
    void modelAlreadyReturningSafeGuidanceIsNotAttributedToGuardReplacement() {
        script = success(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        processor.request(command);
        assertCompleted(AnswerPromptTemplates.NO_EVIDENCE_ANSWER, "NO_EVIDENCE");
        assertThat(traceFromApi().at("/steps/guard/outcome").asText()).isEqualTo("KEPT");
    }

    @Test
    void traceApiRejectsOtherMemberWrongSessionAndUnauthenticatedAccess() throws Exception {
        script = success(SUPPORTED);
        processor.request(command);
        String path = "/api/v1/chat/sessions/" + sessionId + "/executions/" + executionId + "/trace";
        var other = new MockHttpSession();
        other.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId + 999999);
        mockMvc.perform(get(path).session(other)).andExpect(status().isNotFound());
        // Existing guest filter issues an anonymous guest identity; it still cannot own this execution.
        mockMvc.perform(get(path)).andExpect(status().isNotFound());
        var owner = new MockHttpSession();
        owner.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        mockMvc.perform(get("/api/v1/chat/sessions/" + (sessionId + 999999)
                        + "/executions/" + executionId + "/trace").session(owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void guestTraceUsesSameOwnershipBoundaryAsHistory() throws Exception {
        UUID guest = UUID.randomUUID();
        jdbc.update("INSERT INTO guests(guest_id,expires_at) VALUES (?,now()+interval '1 day')", guest);
        try {
            jdbc.update("UPDATE chat_sessions SET user_id=NULL,guest_id=? WHERE session_id=?", guest, sessionId);
            var owner = new MockHttpSession();
            owner.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guest);
            var other = new MockHttpSession();
            other.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, UUID.randomUUID());
            String path = "/api/v1/chat/sessions/" + sessionId + "/executions/" + executionId + "/trace";
            mockMvc.perform(get(path).session(owner)).andExpect(status().isOk());
            mockMvc.perform(get(path).session(other)).andExpect(status().isNotFound());
        } finally {
            jdbc.update("UPDATE chat_sessions SET user_id=?,guest_id=NULL WHERE session_id=?", userId, sessionId);
            jdbc.update("DELETE FROM guests WHERE guest_id=?", guest);
        }
    }

    @Test
    void concurrentRequestsKeepQuestionsSourcesAttemptsAndAnswersInTheirOwnExecution() throws Exception {
        long secondSession = jdbc.queryForObject(
                "INSERT INTO chat_sessions(user_id,title) VALUES (?,'동시 추적') RETURNING session_id",
                Long.class, userId);
        long secondInput = jdbc.queryForObject(
                "INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,status)"
                        + " VALUES (?,1,'USER','QUESTION','별도 질문','COMPLETED') RETURNING message_id",
                Long.class, secondSession);
        long secondExecution = jdbc.queryForObject(
                "INSERT INTO chat_executions(session_id,input_message_id,status) VALUES (?,?,'RUNNING')"
                        + " RETURNING execution_id", Long.class, secondSession, secondInput);
        long secondRequest = jdbc.queryForObject(
                "INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)"
                        + " VALUES (?,?,1,'FAQ','별도 질문') RETURNING consult_request_id",
                Long.class, secondSession, secondInput);
        var secondCommand = new ChatProcessingCommand(secondExecution, secondSession, secondInput, "별도 질문");
        var secondPrepared = consult.prepareTurn(secondSession, secondRequest, Purpose.GENERAL_FAQ,
                Map.of(), LocationStatus.MISSING);
        when(analyzer.analyze(secondCommand)).thenReturn(new AnalyzedTurn(secondPrepared, null,
                Purpose.GENERAL_FAQ, "별도 질문", "별도 질문"));
        when(search.search(any())).thenAnswer(invocation -> {
            var request = (com.telme.faq.dto.req.FaqSearchRequest) invocation.getArgument(0);
            boolean second = request.query().equals("별도 질문");
            return List.of(new FaqSearchResponse(second ? 222L : 111L, null, "USIM",
                    second ? "SECOND_FAQ" : "FIRST_FAQ", SUPPORTED, 0.9, 1,
                    LocalDate.of(2026, 10, 1), 1, null));
        });
        emitters.register(secondExecution, new SseEmitter() {
            @Override public void send(SseEventBuilder builder) {}
            @Override public void complete() {}
        });
        script = stream -> { stream.onToken(SUPPORTED); stream.onComplete(); };
        try {
            var first = CompletableFuture.runAsync(() -> processor.request(command));
            var second = CompletableFuture.runAsync(() -> processor.request(secondCommand));
            CompletableFuture.allOf(first, second).get(15, TimeUnit.SECONDS);
            var firstTrace = traceFromApi();
            JsonNode secondTrace = objectMapper.valueToTree(
                    traces.get(new ChatActor(userId, null), secondSession, secondExecution));
            assertThat(firstTrace.path("originalUserMessage").asText()).isEqualTo(QUERY);
            assertThat(secondTrace.path("originalUserMessage").asText()).isEqualTo("별도 질문");
            assertThat(firstTrace.at("/steps/generationInput/sources/0/question").asText()).isEqualTo("FIRST_FAQ");
            assertThat(secondTrace.at("/steps/generationInput/sources/0/question").asText()).isEqualTo("SECOND_FAQ");
            assertThat(firstTrace.path("modelAttempts")).hasSize(1);
            assertThat(secondTrace.path("modelAttempts")).hasSize(1);
            assertThat(firstTrace.at("/modelAttempts/0/generationId").asLong())
                    .isNotEqualTo(secondTrace.at("/modelAttempts/0/generationId").asLong());
            assertThat(secondTrace.path("finalAnswer").asText()).isEqualTo(SUPPORTED);
            assertCompleted(SUPPORTED, "GROUNDED");
        } finally {
            jdbc.update("DELETE FROM consult_conditions WHERE consult_request_id=?", secondRequest);
            jdbc.update("DELETE FROM consult_requests WHERE consult_request_id=?", secondRequest);
            jdbc.update("DELETE FROM chat_sessions WHERE session_id=?", secondSession);
        }
    }

    @Test
    void atomicStageWritesKeepParallelMetadataWithoutOverwritingOtherSteps() throws Exception {
        var first = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < 8; i++) traces.append(executionId, "parallel", Map.of("attempt", i));
        });
        var second = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < 8; i++) traces.append(executionId, "parallel", Map.of("attempt", i + 8));
            traces.stage(executionId, "anotherStage", Map.of("status", "PRESENT"));
        });
        CompletableFuture.allOf(first, second).get(10, TimeUnit.SECONDS);
        assertThat(traceFromApi().at("/steps/parallel")).hasSize(16);
        assertThat(traceFromApi().at("/steps/anotherStage/status").asText()).isEqualTo("PRESENT");
    }

    @Test
    void additiveMigrationPreservesExistingExecutionsAndModelAttempts() throws Exception {
        String schema = "trace_compat_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            jdbc.execute("CREATE TABLE " + schema
                    + ".chat_executions(execution_id bigint PRIMARY KEY, status varchar(20))");
            jdbc.execute("CREATE TABLE " + schema
                    + ".llm_generations(generation_id bigint PRIMARY KEY, status varchar(30))");
            jdbc.execute("INSERT INTO " + schema + ".chat_executions VALUES (1,'FAILED')");
            jdbc.execute("INSERT INTO " + schema + ".llm_generations VALUES (1,'TIMEOUT')");
            try (var resource = getClass().getResourceAsStream(
                    "/db/migration/V19__add_execution_pipeline_trace.sql")) {
                assertThat(resource).isNotNull();
                String migration = new String(resource.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                        .replace("chat_executions", schema + ".chat_executions")
                        .replace("llm_generations", schema + ".llm_generations");
                jdbc.execute(migration);
            }
            assertThat(jdbc.queryForObject("SELECT status FROM " + schema
                    + ".chat_executions WHERE execution_id=1", String.class)).isEqualTo("FAILED");
            assertThat(jdbc.queryForObject("SELECT pipeline_trace::text FROM " + schema
                    + ".chat_executions WHERE execution_id=1", String.class)).isNull();
            assertThat(jdbc.queryForObject("SELECT status FROM " + schema
                    + ".llm_generations WHERE generation_id=1", String.class)).isEqualTo("TIMEOUT");
            assertThat(jdbc.queryForObject("SELECT request_options::text FROM " + schema
                    + ".llm_generations WHERE generation_id=1", String.class)).isNull();
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void multipleGenerationsInOneExecutionKeepConsultationReferencesAndGuardResults() {
        long secondRequest = jdbc.queryForObject(
                "INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)"
                        + " VALUES (?,?,2,'FAQ','하위 질문') RETURNING consult_request_id",
                Long.class, sessionId, command.inputMessageId());
        script = success(SUPPORTED);
        var sink = new LlmStreamHandler() {
            @Override public void onToken(String token) {}
            @Override public void onComplete() {}
            @Override public void onError(Throwable error) {}
        };
        for (long consultation : new long[] {requestId, secondRequest}) {
            answerGenerator.generate(com.telme.rag.dto.req.AnswerRequest.builder()
                    .executionId(executionId).consultRequestId(consultation).userQuery(QUERY)
                    .searchResults(List.of(new FaqSearchResponse(consultation, null, "USIM",
                            "하위 질문 근거", SUPPORTED, 0.9, 1, LocalDate.of(2026, 10, 1), 1, null)))
                    .build(), sink);
        }
        var trace = traceFromApi();
        assertThat(trace.at("/steps/generationInputs")).hasSize(2);
        assertThat(trace.at("/steps/guardResults")).hasSize(2);
        assertThat(trace.path("steps").has("generationInput")).isFalse();
        assertThat(trace.at("/steps/generationInputs/0/consultRequestId").asLong()).isEqualTo(requestId);
        assertThat(trace.at("/steps/generationInputs/1/consultRequestId").asLong()).isEqualTo(secondRequest);
        assertThat(trace.at("/steps/guardResults/0/consultRequestId").asLong()).isEqualTo(requestId);
        assertThat(trace.at("/steps/guardResults/1/consultRequestId").asLong()).isEqualTo(secondRequest);
        assertThat(trace.at("/modelAttempts/0/configuration/consultRequestId").asLong()).isEqualTo(requestId);
        assertThat(trace.at("/modelAttempts/1/configuration/consultRequestId").asLong()).isEqualTo(secondRequest);
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
        assertThat(traceFromApi().at("/steps/finalTransmission/status").asText()).isEqualTo("DISPATCH_RETURNED");
    }

    private void assertCompletedHistory(String answer, String basis) {
        var trace = traceFromApi();
        assertThat(trace.path("executionId").asLong()).isEqualTo(executionId);
        assertThat(trace.path("originalUserMessage").asText()).isEqualTo(QUERY);
        assertThat(trace.path("finalAnswer").asText()).isEqualTo(answer);
        assertThat(trace.path("answerBasis").asText()).isEqualTo(basis);
        assertThat(trace.at("/steps/finalTransmission/outputMessageId").asLong())
                .isEqualTo(trace.path("outputMessageId").asLong());
        assertThat(trace.at("/steps/finalTransmission/status").asText())
                .isIn("DISPATCH_RETURNED", "DISPATCH_ERROR");
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
        var trace = traceFromApi();
        assertThat(trace.path("executionId").asLong()).isEqualTo(executionId);
        assertThat(trace.path("originalUserMessage").asText()).isEqualTo(QUERY);
        assertThat(trace.path("status").asText()).isEqualTo(expectedStatus);
        assertThat(trace.path("errorCode").asText()).isEqualTo(errorCode);
        assertThat(trace.path("finalAnswer").isNull()).isTrue();
        assertThat(trace.path("steps").has("finalTransmission")).isFalse();
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

    private JsonNode traceFromApi() {
        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        try {
            var response = mockMvc.perform(get("/api/v1/chat/sessions/" + sessionId
                            + "/executions/" + executionId + "/trace").session(httpSession))
                    .andExpect(status().isOk()).andReturn().getResponse();
            return objectMapper.readTree(response.getContentAsByteArray()).path("result");
        } catch (Exception exception) {
            throw new AssertionError("Execution trace API verification failed", exception);
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
                assertThat(jdbc.queryForObject(
                        "SELECT COALESCE(jsonb_exists(pipeline_trace, 'finalTransmission'), false)"
                                + " FROM chat_executions WHERE execution_id=?",
                        Boolean.class, executionId)).isFalse();
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
