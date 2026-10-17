package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ChatProcessingPort;
import com.telme.chat.service.ExecutionTrace;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.service.ConsultChatProcessingService.AnalyzedTurn;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.Prepared;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.ContextProvider;
import com.telme.consult.service.FollowupSelectionValidator.Selection;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.service.SubscriptionCommandService;
import com.telme.subscription.service.SubscriptionQueryService;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 변경 요청 -> 예/아니요 확인 -> 기존 요금제 변경까지 실제 채팅 API·상담 저장·구독 DB를 거쳐 확인한다. 시연 모드라 변경이 바로 적용된다
@SpringBootTest(properties = {
        "telme.consult.persistence-enabled=true",
        "telme.subscription.demo-mode=true",
        "spring.datasource.hikari.maximum-pool-size=2"
})
@AutoConfigureMockMvc(addFilters = false)
class PlanChangeChatIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsultChatPersistenceService persistence;
    @Autowired ConsultTurnPreparationService turns;
    @Autowired ContextProvider contexts;
    @Autowired PlanChangeAnswerer changes;
    @Autowired @Qualifier("consultAnswerProvider") AnswerProvider wiredAnswers;
    @Autowired PlanRepository plans;
    @Autowired SubscriptionCommandService subscriptions;
    @Autowired SubscriptionQueryService subscriptionQueries;
    @MockitoBean ChatProcessingPort processing;

    final AtomicLong requestId = new AtomicLong();
    final AtomicReference<String> question = new AtomicReference<>();
    long userId;
    MockHttpSession identity;

    @BeforeEach
    void setUp() {
        userId = jdbc.queryForObject("INSERT INTO users(email,name) VALUES (?,'변경 검증') RETURNING user_id",
                Long.class, "plan-change-" + UUID.randomUUID() + "@example.com");
        identity = new MockHttpSession();
        identity.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        subscriptions.subscribe(userId, planId("LTE 맥스"), null);

        AnswerProvider faq = mock(AnswerProvider.class);
        when(faq.clarifies()).thenReturn(true);
        when(faq.prepare(any(), anyBoolean())).thenReturn(Prepared.none());
        var answers = new PlanChangeAnswerProvider(faq, changes, sessionId -> Optional.of(userId), ExecutionTrace.noop(), true);
        var actualProcessing = new ConsultChatProcessingService(this::analyze, answers, persistence,
                new ConfirmedConditionConverter());
        doAnswer(call -> {
            actualProcessing.request(call.getArgument(0));
            return null;
        }).when(processing).request(any());
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM consult_conditions WHERE consult_request_id IN (SELECT consult_request_id FROM"
                + " consult_requests WHERE session_id IN (SELECT session_id FROM chat_sessions WHERE user_id=?))", userId);
        jdbc.update("DELETE FROM consult_requests WHERE session_id IN (SELECT session_id FROM chat_sessions"
                + " WHERE user_id=?)", userId);
        jdbc.update("DELETE FROM chat_sessions WHERE user_id=?", userId);
        jdbc.update("DELETE FROM subscription_usages WHERE subscription_id IN"
                + " (SELECT subscription_id FROM user_subscriptions WHERE user_id=?)", userId);
        jdbc.update("DELETE FROM subscription_histories WHERE subscription_id IN"
                + " (SELECT subscription_id FROM user_subscriptions WHERE user_id=?)", userId);
        jdbc.update("DELETE FROM user_subscriptions WHERE user_id=?", userId);
        jdbc.update("DELETE FROM users WHERE user_id=?", userId);
    }

    @Test
    void 변경을_요청하면_먼저_확인하고_예라고_하면_요금제가_바뀐다() throws Exception {
        long sessionId = createSession();

        waitCompleted(send(sessionId, "5G 라이트로 바꿔줘"));
        JsonNode first = history(sessionId).path("result").path("messages");
        assertThat(first.get(1).path("messageType").asText()).isEqualTo("CLARIFICATION");
        assertThat(first.get(1).path("content").asText()).contains("지금 쓰는 LTE 맥스에서 5G 라이트", "바꿀까요?");
        assertThat(currentPlan()).isEqualTo("LTE 맥스");

        waitCompleted(send(sessionId, "예"));
        JsonNode last = history(sessionId).path("result").path("messages");
        assertThat(last.get(3).path("messageType").asText()).isEqualTo("ANSWER");
        assertThat(last.get(3).path("content").asText()).isEqualTo("5G 라이트로 바꿨어요. 지금부터 적용돼요.");
        assertThat(currentPlan()).isEqualTo("5G 라이트");
    }

    @Test
    void 확인에서_아니요라고_하면_요금제를_바꾸지_않는다() throws Exception {
        long sessionId = createSession();

        waitCompleted(send(sessionId, "5G 라이트로 바꿔줘"));
        waitCompleted(send(sessionId, "아니"));

        JsonNode last = history(sessionId).path("result").path("messages");
        assertThat(last.get(3).path("content").asText()).contains("바꾸지 않았어요");
        assertThat(currentPlan()).isEqualTo("LTE 맥스");
    }

    @Test
    void 실제_앱에서는_기존_상담_답변_빈을_변경_요청_처리기가_감싼다() {
        assertThat(wiredAnswers).isInstanceOf(PlanChangeAnswerProvider.class);
    }

    private String currentPlan() {
        return subscriptionQueries.getMine(userId).subscription().currentPlan().name();
    }

    private long planId(String name) {
        return plans.findByStatusOrderBySortOrderAscPlanIdAsc(com.telme.plan.entity.Plan.Status.ACTIVE).stream()
                .filter(plan -> plan.getName().equals(name)).findFirst().orElseThrow().getPlanId();
    }

    private AnalyzedTurn analyze(ChatProcessingCommand command) {
        if (requestId.get() == 0) {
            question.set(command.content());
            long id = jdbc.queryForObject("INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,"
                    + "intent,query_text) VALUES (?,?,1,'FAQ',?) RETURNING consult_request_id", Long.class,
                    command.sessionId(), command.inputMessageId(), command.content());
            requestId.set(id);
            return new AnalyzedTurn(turns.prepareAnalysis(command.sessionId(),
                    new IntentSubQueryResponse(id, (short) 1, ConsultRequest.Intent.FAQ, command.content(), Map.of()),
                    LocationStatus.MISSING, null), null, Purpose.GENERAL_FAQ, command.content(), command.content());
        }
        var context = contexts.loadVerified(command);
        var candidate = context.candidates().getFirst();
        // 실제 분석기처럼 선택지 답("아니" -> "아니요")을 선택지 값으로 맞춘다
        String answer = command.content().startsWith("아니") ? PlanChangeAnswerer.NO : command.content();
        var followup = turns.prepareFollowup(context, new Selection(candidate.consultRequestId(),
                candidate.questionMessageId(), candidate.field(), Map.of(candidate.field(), Condition.filled(answer))),
                LocationStatus.MISSING);
        return new AnalyzedTurn(followup.preparation(), followup.followup().answeredField(), followup.purpose(),
                followup.originalUserQuery(), followup.searchQuery());
    }

    private long createSession() throws Exception {
        var result = mvc.perform(post("/api/v1/chat/sessions").session(identity).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"변경\"}")).andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray()).path("result").path("sessionId").asLong();
    }

    private long send(long sessionId, String content) throws Exception {
        var result = mvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId).session(identity)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("content", content))))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray()).path("result").path("executionId").asLong();
    }

    private JsonNode history(long sessionId) throws Exception {
        var result = mvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId).session(identity))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void waitCompleted(long executionId) {
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_executions WHERE execution_id=?", String.class, executionId))
                .isEqualTo("COMPLETED"));
    }
}
