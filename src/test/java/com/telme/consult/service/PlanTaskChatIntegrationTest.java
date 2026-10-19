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
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.service.ConsultChatProcessingService.AnalyzedTurn;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.Prepared;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;
import com.telme.plan.repository.PlanRepository;
import com.telme.subscription.service.SubscriptionCommandService;
import com.telme.subscription.service.SubscriptionQueryService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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

// 요금제 업무 요청이 실제 채팅 API·상담 저장을 거쳐 안내와 처리 화면 링크로 답하고, 채팅에서는 구독을 바꾸지 않는지 확인한다
@SpringBootTest(properties = {
        "telme.consult.persistence-enabled=true",
        "telme.subscription.demo-mode=true",
        "spring.datasource.hikari.maximum-pool-size=2"
})
@AutoConfigureMockMvc(addFilters = false)
class PlanTaskChatIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsultChatPersistenceService persistence;
    @Autowired ConsultTurnPreparationService turns;
    @Autowired List<PlanTaskHandler> handlers;
    @Autowired @Qualifier("consultAnswerProvider") AnswerProvider wiredAnswers;
    @Autowired PlanRepository plans;
    @Autowired SubscriptionCommandService subscriptions;
    @Autowired SubscriptionQueryService subscriptionQueries;
    @Autowired com.telme.consult.repository.SessionRecommendationAnswers recommendations;
    @MockitoBean ChatProcessingPort processing;

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
        var answers = new PlanTaskAnswerProvider(faq, handlers, sessionId -> Optional.of(userId),
                recommendations::latestRecommendation, ExecutionTrace.noop(), true);
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
        jdbc.update("DELETE FROM subscription_pauses WHERE subscription_id IN"
                + " (SELECT subscription_id FROM user_subscriptions WHERE user_id=?)", userId);
        jdbc.update("DELETE FROM subscription_histories WHERE subscription_id IN"
                + " (SELECT subscription_id FROM user_subscriptions WHERE user_id=?)", userId);
        jdbc.update("DELETE FROM user_subscriptions WHERE user_id=?", userId);
        jdbc.update("DELETE FROM users WHERE user_id=?", userId);
    }

    @Test
    void 변경_요청에는_안내와_변경_화면_링크만_주고_요금제는_바꾸지_않는다() throws Exception {
        JsonNode answer = ask("5G 라이트로 바꿔줘");

        assertThat(answer.path("messageType").asText()).isEqualTo("ANSWER");
        assertThat(answer.path("content").asText()).contains("지금 쓰는 LTE 맥스에서 5G 라이트", "요금제 변경 화면에서");
        assertThat(answer.path("followUps").toString()).contains("5G 라이트 변경하러 가기");
        assertThat(currentPlan()).isEqualTo("LTE 맥스");
    }

    @Test
    void 신청_요청에는_가입_화면_링크만_주고_가입시키지_않는다() throws Exception {
        subscriptions.cancel(userId);

        JsonNode answer = ask("요금제 신청해줘");

        assertThat(answer.path("content").asText()).startsWith("요금제 가입 화면에서");
        assertThat(answer.path("followUps").toString()).contains(PlanTaskLinks.SUBSCRIBE);
        assertThat(subscriptionQueries.getMine(userId).subscribed()).isFalse();
    }

    @Test
    void 이름_없는_가입_요청은_채팅방에_저장된_직전_추천_요금제로_안내한다() throws Exception {
        subscriptions.cancel(userId);
        long sessionId = createSession();
        insertAssistantAnswer(sessionId, 1, "한 달에 데이터를 40GB 쓰신다면 5G 스탠다드(월 65,000원, 데이터 50GB) 요금제를 추천해요."
                + " 5G로 쓰시려면 5G 프리미엄(월 85,000원, 데이터 150GB) 요금제가 가장 저렴해요.");

        waitCompleted(send(sessionId, "그 요금제로 가입하고싶어"));

        JsonNode messages = history(sessionId).path("result").path("messages");
        String content = messages.get(messages.size() - 1).path("content").asText();
        assertThat(content).startsWith("5G 스탠다드(월 65,000원, 데이터 50GB)는 요금제 가입 화면에서 신청할 수 있어요.");
        assertThat(content).doesNotContain("5G 프리미엄");
        assertThat(messages.get(messages.size() - 1).path("followUps").toString()).contains("5G 스탠다드 가입하러 가기");
    }

    @Test
    void 추천_기록이_없으면_이름_없는_가입_요청은_가입_화면에서_고르도록_안내한다() throws Exception {
        subscriptions.cancel(userId);
        long sessionId = createSession();

        waitCompleted(send(sessionId, "요금제 가입하고 싶어"));

        JsonNode messages = history(sessionId).path("result").path("messages");
        assertThat(messages.get(messages.size() - 1).path("content").asText())
                .startsWith("요금제 가입 화면에서 원하는 요금제를 골라 신청해 주세요.");
    }

    @Test
    void 가장_최근_추천_답변만_읽고_추천이_아닌_답변은_건너뛴다() throws Exception {
        long sessionId = createSession();
        insertAssistantAnswer(sessionId, 1, "한 달에 데이터를 3GB 쓰신다면 LTE 베이직(월 33,000원, 데이터 4GB) 요금제를 추천해요.");
        insertAssistantAnswer(sessionId, 2, "한 달에 데이터를 40GB 쓰신다면 5G 스탠다드(월 65,000원, 데이터 50GB) 요금제를 추천해요.");
        insertAssistantAnswer(sessionId, 3, "요금제 가입 화면에서 신청해 주세요.");

        assertThat(recommendations.latestRecommendation(sessionId)).hasValueSatisfying(content ->
                assertThat(content).startsWith("한 달에 데이터를 40GB"));
        assertThat(recommendations.latestRecommendation(createSession())).isEmpty();
    }

    private void insertAssistantAnswer(long sessionId, int sequence, String content) {
        jdbc.update("INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,status,completed_at)"
                + " VALUES (?,?,'ASSISTANT','ANSWER',?,'COMPLETED',now())", sessionId, sequence, content);
    }

    @Test
    void 일시_정지_요청에는_안내와_링크만_주고_정지하지_않는다() throws Exception {
        JsonNode answer = ask("7일 일시 정지해줘");

        assertThat(answer.path("content").asText()).contains("오늘 7일 정지하면", "기본료의 50%만");
        assertThat(answer.path("followUps").toString()).contains(PlanTaskLinks.PAUSE);
        assertThat(subscriptionQueries.getMine(userId).subscription().pause()).isNull();
    }

    @Test
    void 해지_요청에는_영향과_링크만_주고_해지하지_않는다() throws Exception {
        JsonNode answer = ask("요금제 해지해줘");

        assertThat(answer.path("content").asText()).contains("LTE 맥스를 해지하면 바로 끊기고", "위약금");
        assertThat(answer.path("followUps").toString()).contains(PlanTaskLinks.CANCEL);
        assertThat(subscriptionQueries.getMine(userId).subscribed()).isTrue();
    }

    @Test
    void 실제_앱에서는_기존_상담_답변_빈을_요금제_업무_처리기가_감싼다() {
        assertThat(wiredAnswers).isInstanceOf(PlanTaskAnswerProvider.class);
    }

    // 새 대화방에서 한 번 묻고 그 답변 메시지를 돌려준다
    private JsonNode ask(String content) throws Exception {
        long sessionId = createSession();
        waitCompleted(send(sessionId, content));
        return history(sessionId).path("result").path("messages").get(1);
    }

    private String currentPlan() {
        return subscriptionQueries.getMine(userId).subscription().currentPlan().name();
    }

    private long planId(String name) {
        return plans.findByStatusOrderBySortOrderAscPlanIdAsc(com.telme.plan.entity.Plan.Status.ACTIVE).stream()
                .filter(plan -> plan.getName().equals(name)).findFirst().orElseThrow().getPlanId();
    }

    // 대화방마다 한 번만 묻는다. 의도 분류는 실제 모델 대신 FAQ 요청으로 정한다
    private AnalyzedTurn analyze(ChatProcessingCommand command) {
        long id = jdbc.queryForObject("INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,"
                + "intent,query_text) VALUES (?,?,1,'FAQ',?) RETURNING consult_request_id", Long.class,
                command.sessionId(), command.inputMessageId(), command.content());
        return new AnalyzedTurn(turns.prepareAnalysis(command.sessionId(),
                new IntentSubQueryResponse(id, (short) 1, ConsultRequest.Intent.FAQ, command.content(), Map.of()),
                LocationStatus.MISSING), null, Purpose.GENERAL_FAQ, command.content(), command.content());
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
