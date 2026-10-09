package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.converter.ChatMessageConverter;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.safety.ChatOutputGuard;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatFailure;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ChatProcessingPort;
import com.telme.chat.service.ChatSessionService;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.dto.ClarificationPlan;
import com.telme.consult.dto.MissingCondition;
import com.telme.consult.service.ConsultChatProcessingService.AnalyzedTurn;
import com.telme.consult.service.ConsultChatProcessingService.FaqTurn;
import com.telme.consult.service.ConsultChatProcessingService.TurnAnalyzer;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 검색·모델을 재생하며 실제 Guard·최종 저장·실행 상태·SSE·이력 조회를 연결한다. */
@AutoConfigureMockMvc
@SpringBootTest(properties = {
        "telme.consult.llm-enabled=false",
        "llm.provider=ollama",
        "llm.model=output-safety-replay",
        "llm.retry.wait-duration=0ms",
        "rag.evidence-check.enabled=false",
        "chat.title.enabled=false",
        "chat.summary.trigger-messages=1000",
        "spring.datasource.hikari.maximum-pool-size=3"
})
class ChatOutputSafetyIntegrationTest {

    private static final String QUERY = "유심 재발급 비용 알려주세요";
    private static final String SAFE = "재발급 비용은 7,700원입니다.";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private ConsultService consult;
    @Autowired private ChatProcessingPort processor;
    @Autowired private ChatSessionService sessions;
    @Autowired private ChatEmitterRegistry emitters;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @MockitoBean private TurnAnalyzer analyzer;
    @MockitoBean private FaqSearchService search;
    @MockitoBean(name = "baseLlmClient", enforceOverride = true) private LlmClient model;
    @MockitoSpyBean private ChatOutputGuard outputGuard;
    @MockitoSpyBean private ChatMessageConverter messageConverter;

    private long userId;
    private long sessionId;
    private long executionId;
    private long consultId;
    private long faqId;
    private ChatProcessingCommand command;
    private CaptureEmitter emitter;
    private Consumer<LlmStreamHandler> script;

    @BeforeEach
    void 합성_질문과_모델_재생을_준비한다() {
        userId = jdbc.queryForObject(
                "INSERT INTO users(email,name) VALUES (?,'출력 검사 테스트') RETURNING user_id",
                Long.class, "output-safety-" + UUID.randomUUID() + "@example.com");
        sessionId = jdbc.queryForObject(
                "INSERT INTO chat_sessions(user_id,title) VALUES (?,'출력 검사') RETURNING session_id",
                Long.class, userId);
        long inputId = jdbc.queryForObject(
                "INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,status,completed_at)"
                        + " VALUES (?,1,'USER','QUESTION',?,'COMPLETED',now()) RETURNING message_id",
                Long.class, sessionId, QUERY);
        executionId = jdbc.queryForObject(
                "INSERT INTO chat_executions(session_id,input_message_id,status)"
                        + " VALUES (?,?,'RUNNING') RETURNING execution_id",
                Long.class, sessionId, inputId);
        consultId = newConsult(sessionId, inputId, QUERY);
        command = new ChatProcessingCommand(executionId, sessionId, inputId, QUERY);
        when(analyzer.analyze(command)).thenReturn(faqTurn(consultId, QUERY));
        faqId = jdbc.queryForObject(
                "INSERT INTO faqs(category,question,answer) VALUES ('USIM',?,?) RETURNING faq_id",
                Long.class, QUERY, SAFE);
        when(search.search(any())).thenReturn(List.of(new FaqSearchResponse(
                faqId, null, "USIM", QUERY, SAFE, 0.9, 1, LocalDate.of(2026, 9, 17), 1, null)));
        script = stream -> {
            stream.onToken(SAFE);
            stream.onComplete();
        };
        doAnswer(invocation -> {
            script.accept(invocation.getArgument(1));
            return null;
        }).when(model).stream(any(), any());
        emitter = new CaptureEmitter();
        emitters.register(executionId, emitter);
    }

    @AfterEach
    void 합성_자료만_정리한다() {
        emitter.disconnect();
        jdbc.update("DELETE FROM consult_conditions WHERE consult_request_id IN"
                + " (SELECT consult_request_id FROM consult_requests WHERE session_id IN"
                + " (SELECT session_id FROM chat_sessions WHERE user_id=?))", userId);
        jdbc.update("DELETE FROM consult_requests WHERE session_id IN"
                + " (SELECT session_id FROM chat_sessions WHERE user_id=?)", userId);
        jdbc.update("DELETE FROM chat_sessions WHERE user_id=?", userId);
        jdbc.update("DELETE FROM users WHERE user_id=?", userId);
        jdbc.update("DELETE FROM faqs WHERE faq_id=?", faqId);
    }

    @Test
    @DisplayName("생성 토큰의 욕설은 미리 노출하지 않고 안전 안내·실패 사유만 저장·전송한다")
    void 욕설_생성문은_정상_답변으로_남기지_않는다() throws Exception {
        assertThat(processor).isInstanceOf(ConsultChatProcessingService.class);
        script = stream -> {
            stream.onToken("씨");
            assertThat(emitter.tokens()).isEmpty();
            stream.onToken("발. " + SAFE);
            assertThat(emitter.tokens()).isEmpty();
            stream.onComplete();
            assertThat(emitter.tokens()).isEmpty();
        };
        processor.request(command);
        assertBlocked();
        assertThat(jdbc.queryForList(
                "SELECT status FROM llm_generations WHERE execution_id=?", String.class, executionId))
                .containsExactly("SUCCESS");
        // 실제 모델 호출 완료와 최종 전달 차단은 서로 다른 결과다.
        assertThat(trace().at("/steps/outputSafety/outcome").asText()).isEqualTo("BLOCKED");
        assertThat(trace().at("/steps/outputSafety/ruleIds/0").asText()).startsWith("OUTPUT_");
        assertThat(trace().at("/steps/outputSafety").toString()).doesNotContain("씨발", SAFE);
    }

    @Test
    @DisplayName("출력 검사 후 정상 근거 답변은 저장된 동일 문자열을 한 번 전송한다")
    void 정상_답변의_저장과_전송을_유지한다() {
        processor.request(command);
        assertThat(emitter.tokens()).containsExactly(SAFE);
        assertThat(emitter.names()).containsExactly("start", "token", "complete");
        assertThat(historyAnswer().content()).isEqualTo(SAFE);
        assertThat(historyAnswer().answerBasis()).isEqualTo("GROUNDED");
        assertThat(historyAnswer().status()).isEqualTo("COMPLETED");
        long messageId = historyAnswer().messageId();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM message_sources WHERE message_id=?", Integer.class, messageId))
                .isEqualTo(1));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM consult_requests WHERE consult_request_id=?", String.class, consultId))
                .isEqualTo("DONE");
    }

    @Test
    @DisplayName("모델을 거치지 않는 직접 답변도 최종 저장 경계에서 검사한다")
    void 직접_응답도_출력_검사를_우회하지_않는다() throws Exception {
        when(analyzer.analyze(command)).thenReturn(AnalyzedTurn.direct(new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "병신입니다.", null, List.of(), null)));
        processor.request(command);
        assertBlocked();
        verify(model, never()).stream(any(), any());
    }

    @Test
    @DisplayName("본문이 정상이더라도 추천 질문의 욕설은 저장하지 않는다")
    void 추천_질문을_포함한_실제_출력을_차단한다() throws Exception {
        when(analyzer.analyze(command)).thenReturn(AnalyzedTurn.direct(new ChatAnswer(
                ChatMessage.MessageType.ANSWER, SAFE, ChatMessage.AnswerBasis.GROUNDED,
                List.of("씨발 요금도 알려줘"), null)));
        processor.request(command);
        assertBlocked();
        assertThat(trace().at("/steps/outputSafety/field").asText()).isEqualTo("followUps");
    }

    @Test
    @DisplayName("검사 이후 추천 목록이 바뀌어도 실제 저장할 JSON을 다시 검사해 원문 노출을 막는다")
    void 저장할_문구와_검사한_문구가_어긋나지_않는다() throws Exception {
        List<String> suggestions = new ArrayList<>(List.of("납부 방법도 알려주세요"));
        when(analyzer.analyze(command)).thenReturn(AnalyzedTurn.direct(new ChatAnswer(
                ChatMessage.MessageType.ANSWER, SAFE, ChatMessage.AnswerBasis.GROUNDED, suggestions, null)));
        doAnswer(invocation -> {
            suggestions.set(0, "씨발");
            return invocation.callRealMethod();
        }).when(messageConverter).toJson(same(suggestions));
        processor.request(command);
        assertBlocked();
        assertThat(historyAnswer().followUps()).isNull();
    }

    @Test
    @DisplayName("매장 표시 문구의 욕설은 스냅샷으로 저장되거나 완료 이벤트로 전달되지 않는다")
    void 매장_표시_문구도_저장_전에_차단한다() throws Exception {
        when(analyzer.analyze(command)).thenReturn(AnalyzedTurn.direct(new ChatAnswer(
                ChatMessage.MessageType.STORE_RESULT, "매장을 안내합니다.", null, List.of(),
                List.of(Map.of("storeId", 7, "name", "씨발")))));
        processor.request(command);
        assertBlocked();
        assertThat(historyAnswer().storeResults()).isNull();
    }

    @Test
    @DisplayName("되묻기 선택지가 차단되면 질문·조건·계획을 잘못 저장하지 않는다")
    void 차단된_되묻기는_대기_상태를_임의로_만들지_않는다() throws Exception {
        prepareClarification("어떻게 받으시나요?", List.of("이메일", "씨발"));
        processor.request(command);
        assertBlocked();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM consult_conditions WHERE consult_request_id=?", Integer.class, consultId))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_sessions WHERE session_id=?", String.class, sessionId))
                .isNotEqualTo("NEED_CLARIFICATION");
        verify(model, never()).stream(any(), any());
    }

    @Test
    @DisplayName("정상 되묻기의 질문·선택지·대기 상태는 기존대로 저장된다")
    void 정상_되묻기는_기존_계약을_유지한다() {
        prepareClarification("어떻게 받으시나요?", List.of("이메일", "우편"));
        processor.request(command);
        assertThat(historyAnswer().messageType()).isEqualTo("CLARIFICATION");
        assertThat(historyAnswer().content()).isEqualTo("어떻게 받으시나요?");
        assertThat(historyAnswer().followUps()).containsExactly("이메일", "우편");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM consult_requests WHERE consult_request_id=?", String.class, consultId))
                .isEqualTo("WAITING_CONDITION");
        assertThat(emitter.tokens()).isEmpty();
        assertThat(emitter.names()).containsExactly("complete");
    }

    @Test
    @DisplayName("정상 모델 답변이어도 복합 답변 제목의 고객 욕설 재인용을 차단한다")
    void 복합_제목까지_조립한_최종문을_검사한다() throws Exception {
        String unsafeQuery = "씨발 요금제";
        long secondId = newConsult(sessionId, command.inputMessageId(), unsafeQuery, 2);
        var first = consult.prepareTurn(sessionId, consultId, Purpose.GENERAL_FAQ, Map.of(), LocationStatus.MISSING);
        var second = consult.prepareTurn(sessionId, secondId, Purpose.GENERAL_FAQ, Map.of(), LocationStatus.MISSING);
        when(analyzer.analyze(command)).thenReturn(AnalyzedTurn.multipleFaq(List.of(
                new FaqTurn(first, QUERY), new FaqTurn(second, unsafeQuery))));
        processor.request(command);
        assertBlocked();
        assertThat(jdbc.queryForList(
                "SELECT status FROM consult_requests WHERE session_id=?", String.class, sessionId))
                .doesNotContain("DONE");
    }

    @Test
    @DisplayName("검사 오류가 나도 후보를 전송하지 않고 별도 실패 안내를 제공한다")
    void 검사_오류도_안전하게_종료한다() throws Exception {
        doThrow(new IllegalStateException("검증용 검사 실패")).when(outputGuard).verify(any());
        processor.request(command);
        assertFailure(ChatErrorCode.OUTPUT_CHECK_FAILED);
        assertThat(trace().at("/steps/outputSafety/outcome").asText()).isEqualTo("CHECK_FAILED");
    }

    @Test
    @DisplayName("출력 검사 전에 취소된 생성은 기존 취소 상태와 안내를 유지한다")
    void 취소를_출력_차단으로_바꾸지_않는다() {
        script = stream -> {
            throw new LlmStreamCancelledException();
        };
        processor.request(command);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_executions WHERE execution_id=?", String.class, executionId))
                .isEqualTo("CANCELLED");
        assertThat(emitter.tokens()).isEmpty();
        verify(outputGuard, never()).verify(any());
    }

    @Test
    @DisplayName("정상 입력 접수는 201·실행 ID를 유지하고 이후 출력 차단은 해당 실행의 실패로 끝낸다")
    void 입력_접수와_출력_차단을_구분한다() throws Exception {
        long newSession = jdbc.queryForObject(
                "INSERT INTO chat_sessions(user_id,title) VALUES (?,'접수 확인') RETURNING session_id",
                Long.class, userId);
        // 단독 develop에는 사용자 입력 검사 테이블이 없다. 선행 기능 결합 검증에서는 실제 경고를 준비한다.
        boolean hasInputGuard = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT to_regclass('chat_input_guard_events') IS NOT NULL", Boolean.class));
        if (hasInputGuard) {
            for (int index = 0; index < 2; index++) {
                var warned = sessions.sendMessage(new ChatActor(userId, null), newSession,
                        new com.telme.chat.dto.req.ChatMessageSendRequest("씨발"));
                assertThat(warned.executionId()).isNull();
            }
        }
        when(analyzer.analyze(any())).thenAnswer(invocation -> {
            ChatProcessingCommand accepted = invocation.getArgument(0);
            long id = newConsult(accepted.sessionId(), accepted.inputMessageId(), accepted.content());
            var prepared = consult.prepareTurn(
                    accepted.sessionId(), id, Purpose.GENERAL_FAQ, Map.of(), LocationStatus.MISSING);
            return new AnalyzedTurn(prepared, null, Purpose.GENERAL_FAQ, accepted.content(), accepted.content());
        });
        script = stream -> {
            stream.onToken("씨발. " + SAFE);
            stream.onComplete();
        };
        var response = mvc.perform(post("/api/v1/chat/sessions/{id}/messages", newSession)
                        .session(identity())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("content", QUERY))))
                .andExpect(status().isCreated()).andReturn();
        JsonNode accepted = mapper.readTree(response.getResponse().getContentAsByteArray()).path("result");
        assertThat(accepted.path("executionId").asLong()).isPositive();
        assertThat(accepted.has("inputGuard")).isFalse();
        long newExecution = accepted.path("executionId").asLong();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT error_code FROM chat_executions WHERE execution_id=?", String.class, newExecution))
                .isEqualTo(ChatErrorCode.OUTPUT_POLICY_BLOCKED.getCode()));
        if (hasInputGuard) {
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM chat_input_guard_events WHERE user_id=?", Integer.class, userId))
                    .isEqualTo(2);
            assertThat(jdbc.queryForObject(
                    "SELECT restriction_until FROM chat_input_guard_states WHERE user_id=?",
                    java.sql.Timestamp.class, userId)).isNull();
        }
    }

    private void prepareClarification(String question, List<String> options) {
        ClarificationPlan plan = new ClarificationPlan(List.of(new MissingCondition(
                "delivery_method", question, options, "합성 근거: 이메일 또는 우편으로 수령한다.")));
        var decision = FaqClarificationDecisions.ask(consultId, plan, Map.of());
        int version = consult.prepareTurn(
                sessionId, consultId, Purpose.GENERAL_FAQ, Map.of(), LocationStatus.MISSING)
                .prepared().expectedVersion();
        var prepared = new ConsultService.PreparedTurn(sessionId, version, decision, plan);
        when(analyzer.analyze(command)).thenReturn(new AnalyzedTurn(
                new ConsultService.PreparationResult(prepared, null), null,
                Purpose.GENERAL_FAQ, QUERY, QUERY));
    }

    private long newConsult(long sid, long inputId, String query) {
        return newConsult(sid, inputId, query, 1);
    }

    private long newConsult(long sid, long inputId, String query, int order) {
        return jdbc.queryForObject(
                "INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)"
                        + " VALUES (?,?,?,'FAQ',?) RETURNING consult_request_id",
                Long.class, sid, inputId, order, query);
    }

    private AnalyzedTurn faqTurn(long id, String query) {
        var prepared = consult.prepareTurn(sessionId, id, Purpose.GENERAL_FAQ, Map.of(), LocationStatus.MISSING);
        return new AnalyzedTurn(prepared, null, Purpose.GENERAL_FAQ, query, query);
    }

    private void assertBlocked() throws Exception {
        assertFailure(ChatErrorCode.OUTPUT_POLICY_BLOCKED);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM consult_requests WHERE consult_request_id=?", String.class, consultId))
                .isNotEqualTo("DONE");
    }

    private void assertFailure(ChatErrorCode code) throws Exception {
        assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_executions WHERE execution_id=?", String.class, executionId))
                .isEqualTo("FAILED");
        assertThat(historyAnswer().messageType()).isEqualTo("ERROR");
        assertThat(historyAnswer().status()).isEqualTo("FAILED");
        assertThat(historyAnswer().content()).isEqualTo(code.getMessage());
        assertThat(historyAnswer().answerBasis()).isNull();
        assertThat(historyAnswer().followUps()).isNull();
        assertThat(historyAnswer().ratable()).isFalse();
        assertThat(emitter.tokens()).isEmpty();
        assertThat(emitter.names()).doesNotContain("complete");
        assertThat(emitter.names().stream().filter("error"::equals).count()).isEqualTo(1);
        JsonNode failure = mapper.valueToTree(emitter.events.getLast().data());
        assertThat(failure.has("outputSafetyFailure")).isFalse();
        assertThat(failure.path("errorCode").asText()).isEqualTo(code.getCode());
        assertThat(failure.path("message").asText()).isEqualTo(historyAnswer().content());
        String replay = replay();
        assertThat(replay).contains("event:error", code.getCode(), code.getMessage());
        assertThat(replay).doesNotContain("event:token", "event:complete", "씨발", "병신");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM chat_messages WHERE session_id=? AND role='ASSISTANT'",
                Integer.class, sessionId)).isEqualTo(1);
    }

    private com.telme.chat.dto.res.ChatMessageHistoryItemResponse historyAnswer() {
        return sessions.getMessages(new ChatActor(userId, null), sessionId, null, 20).messages().getLast();
    }

    private MockHttpSession identity() {
        var identity = new MockHttpSession();
        identity.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        return identity;
    }

    private String replay() throws Exception {
        var subscribed = mvc.perform(get("/api/v1/chat/sessions/{sid}/executions/{eid}/subscribe",
                        sessionId, executionId).session(identity()))
                .andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(subscribed)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode trace() throws Exception {
        var response = mvc.perform(get("/api/v1/chat/sessions/{sid}/executions/{eid}/trace",
                        sessionId, executionId).session(identity()))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray()).path("result");
    }

    private record Event(String name, Object data) {}

    private final class CaptureEmitter extends SseEmitter {
        private final List<Event> events = new ArrayList<>();
        private Runnable disconnected;

        @Override
        public void onCompletion(Runnable callback) {
            disconnected = callback;
        }

        @Override
        public void complete() {}

        void disconnect() {
            if (disconnected != null) {
                disconnected.run();
            }
        }

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
                assertThat(jdbc.queryForObject(
                        "SELECT status FROM chat_executions WHERE execution_id=?", String.class, executionId))
                        .isEqualTo("COMPLETED");
                assertThat(payload).isEqualTo(historyAnswer().content());
            }
            events.add(new Event(name, payload));
        }

        List<String> names() {
            return events.stream().map(Event::name).toList();
        }

        List<String> tokens() {
            return events.stream().filter(event -> "token".equals(event.name()))
                    .map(event -> (String) event.data()).toList();
        }
    }
}
