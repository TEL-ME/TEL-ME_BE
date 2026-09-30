package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.dto.res.ChatMessageHistoryItemResponse;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ChatProcessingPort;
import com.telme.chat.service.ChatSessionService;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnalyzedTurn;
import com.telme.consult.service.ConsultChatProcessingService.TurnAnalyzer;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 검색·모델만 대체하고 실제 처리기, Guard, 트랜잭션, SSE 및 이력 조회를 연결한다. */
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
    }

    @Test
    void deliveryFailureAfterCommitKeepsCompletedAnswerForReconnect() {
        emitter.failTokenDelivery = true;
        script = success(SUPPORTED);
        processor.request(command);

        assertThat(emitter.tokens()).isEmpty();
        assertThat(executionStatus()).isEqualTo("COMPLETED");
        assertThat(historyAnswer().content()).isEqualTo(SUPPORTED);
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
