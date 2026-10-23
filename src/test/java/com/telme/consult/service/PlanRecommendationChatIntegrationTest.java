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
import com.telme.consult.dto.MissingCondition;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.JdbcConsultStateStore;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.AnalyzedTurn;
import com.telme.consult.service.ConsultChatProcessingService.Prepared;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.ContextProvider;
import com.telme.consult.service.FollowupSelectionValidator.Selection;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 추천 질문 -> 나이대 되묻기 -> 선택지로 답 -> 확정된 나이대로 추천 답변까지 실제 채팅 API와 상담 저장을 거쳐 확인한다
@SpringBootTest(properties = {
        "telme.consult.persistence-enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"
})
@AutoConfigureMockMvc(addFilters = false)
class PlanRecommendationChatIntegrationTest {

    static final String QUESTION = "요금제 추천해줘";
    static final String CORRECTION = "아 잘못 말했다 17살이요";
    static final List<String> OPTIONS = List.of("만 14~18세", "만 19~64세", "만 65세 이상");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsultService consult;
    @Autowired JdbcConsultStateStore states;
    @Autowired ConsultChatPersistenceService persistence;
    @Autowired ConsultTurnPreparationService turns;
    @Autowired ContextProvider contexts;
    @Autowired com.telme.consult.repository.SessionRecommendationAnswers answersMemory;
    @MockitoBean ChatProcessingPort processing;

    final AtomicLong requestId = new AtomicLong();
    final AtomicReference<String> askedWith = new AtomicReference<>("미호출");
    final AtomicReference<Boolean> mayAskLast = new AtomicReference<>();
    long userId;
    MockHttpSession identity;

    @BeforeEach
    void setUp() {
        userId = jdbc.queryForObject("INSERT INTO users(email,name) VALUES (?,'추천 검증') RETURNING user_id",
                Long.class, "plan-rec-" + UUID.randomUUID() + "@example.com");
        identity = new MockHttpSession();
        identity.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);

        AnswerProvider faq = mock(AnswerProvider.class);
        when(faq.clarifies()).thenReturn(true);
        when(faq.prepare(any(), anyBoolean())).thenReturn(Prepared.none());
        PlanQuestionClassifier classifier = mock(PlanQuestionClassifier.class);
        when(classifier.classifyQuestion(any(), any())).thenReturn(PlanQuestion.of(PlanQuestionType.RECOMMENDATION));
        MemberRecommendationAnswerer recommendation = mock(MemberRecommendationAnswerer.class);
        when(recommendation.recommend(any(), any(), any(), anyBoolean())).thenAnswer(call -> {
            Map<String, String> confirmed = new java.util.HashMap<>(call.<Map<String, String>>getArgument(2));
            confirmed.putAll(call.getArgument(1));
            boolean mayAsk = call.getArgument(3);
            askedWith.set(confirmed.get("age_group") + "/" + confirmed.get("monthly_data_usage"));
            mayAskLast.set(mayAsk);
            if (confirmed.isEmpty() && mayAsk) {
                return new MemberRecommendationAnswerer.Outcome(null, List.of(), List.of(
                        new MissingCondition("age_group", "나이에 따라 추천하는 요금제가 달라져요. 나이대가 어떻게 되세요?", OPTIONS, null),
                        new MissingCondition("monthly_data_usage", "한 달에 데이터를 얼마나 쓰세요? 대략 몇 GB인지 채팅으로 알려주세요.", List.of(), null)));
            }
            // 3GB는 통화·문자를 함께 계획하고, 통화 600분이면 문자 한도가 없는 요금제로 바뀌어 문자는 더 묻지 않는다
            if (mayAsk && "3GB".equals(confirmed.get("monthly_data_usage")) && !confirmed.containsKey("monthly_voice_usage")) {
                return new MemberRecommendationAnswerer.Outcome(null, List.of(), List.of(
                        new MissingCondition("monthly_voice_usage", "한 달에 통화는 대략 몇 분 쓰세요?", List.of(), null),
                        new MissingCondition("monthly_sms_usage", "한 달에 문자는 대략 몇 건 보내세요?", List.of(), null)));
            }
            if (mayAsk && "3GB".equals(confirmed.get("monthly_data_usage")) && confirmed.containsKey("monthly_voice_usage")
                    && !"600분".equals(confirmed.get("monthly_voice_usage")) && !confirmed.containsKey("monthly_sms_usage")) {
                return new MemberRecommendationAnswerer.Outcome(null, List.of(), List.of(
                        new MissingCondition("monthly_sms_usage", "한 달에 문자는 대략 몇 건 보내세요?", List.of(), null)));
            }
            if (mayAsk && "2GB".equals(confirmed.get("monthly_data_usage")) && !confirmed.containsKey("monthly_voice_usage")) {
                return new MemberRecommendationAnswerer.Outcome(null, List.of(), List.of(
                        new MissingCondition("monthly_voice_usage", "한 달에 통화는 대략 몇 분 쓰세요?", List.of(), null)));
            }
            return new MemberRecommendationAnswerer.Outcome("추천 답변: " + askedWith.get(), List.of(), null);
        });
        var answers = new PlanAnswerProvider(faq, classifier, mock(PlanCatalogAnswerer.class),
                mock(MemberPlanAnswerer.class), recommendation, sessionId -> Optional.of(userId), answersMemory::of,
                ComparisonEvidenceResolver.passthrough(), ExecutionTrace.noop(), true);
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
        jdbc.update("DELETE FROM users WHERE user_id=?", userId);
    }

    @Test
    void 나이대와_데이터_사용량을_차례로_되묻고_확정된_답으로_추천_답변이_나온다() throws Exception {
        long sessionId = createSession();

        waitCompleted(send(sessionId, QUESTION));
        JsonNode first = history(sessionId).path("result").path("messages");
        assertThat(first.size()).isEqualTo(2);
        assertThat(first.get(1).path("messageType").asText()).isEqualTo("CLARIFICATION");
        assertThat(first.get(1).path("content").asText()).contains("나이대가 어떻게 되세요");
        assertThat(mayAskLast.get()).isTrue();

        waitCompleted(send(sessionId, "만 65세 이상"));
        JsonNode second = history(sessionId).path("result").path("messages");
        assertThat(second.size()).isEqualTo(4);
        assertThat(second.get(3).path("messageType").asText()).isEqualTo("CLARIFICATION");
        assertThat(second.get(3).path("content").asText()).contains("한 달에 데이터를 얼마나 쓰세요");

        waitCompleted(send(sessionId, "40GB"));

        assertThat(states.load(sessionId, requestId.get()).conditions().get("age_group"))
                .isEqualTo(Condition.filled("만 65세 이상"));
        assertThat(states.load(sessionId, requestId.get()).conditions().get("monthly_data_usage"))
                .isEqualTo(Condition.filled("40GB"));
        JsonNode last = history(sessionId).path("result").path("messages");
        assertThat(last.size()).isEqualTo(6);
        assertThat(last.get(5).path("messageType").asText()).isEqualTo("ANSWER");
        assertThat(last.get(5).path("content").asText()).isEqualTo("추천 답변: 만 65세 이상/40GB");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consult_requests WHERE session_id=?", Integer.class,
                sessionId)).isEqualTo(1);
        assertThat(answersMemory.of(sessionId)).containsOnly(
                java.util.Map.entry("age_group", "만 65세 이상"), java.util.Map.entry("monthly_data_usage", "40GB"));
        assertThat(answersMemory.of(createSession())).isEmpty();
    }

    @Test
    void 통화_답으로_추천이_바뀌어_문자가_필요_없어지면_문자는_묻지_않고_추천한다() throws Exception {
        long sessionId = createSession();

        waitCompleted(send(sessionId, QUESTION));
        waitCompleted(send(sessionId, "만 19~64세"));
        waitCompleted(send(sessionId, "3GB"));
        JsonNode asked = history(sessionId).path("result").path("messages");
        assertThat(asked.get(asked.size() - 1).path("content").asText()).contains("통화는 대략 몇 분");

        waitCompleted(send(sessionId, "600분"));

        JsonNode last = history(sessionId).path("result").path("messages");
        assertThat(last.size()).isEqualTo(8);
        assertThat(last.get(7).path("messageType").asText()).isEqualTo("ANSWER");
        assertThat(last.get(7).path("content").asText()).isEqualTo("추천 답변: 만 19~64세/3GB");
    }

    @Test
    void 통화_답을_받은_뒤에도_문자가_필요하면_문자를_묻는다() throws Exception {
        long sessionId = createSession();

        waitCompleted(send(sessionId, QUESTION));
        waitCompleted(send(sessionId, "만 19~64세"));
        waitCompleted(send(sessionId, "3GB"));
        waitCompleted(send(sessionId, "100분"));

        JsonNode asked = history(sessionId).path("result").path("messages");
        assertThat(asked.size()).isEqualTo(8);
        assertThat(asked.get(7).path("messageType").asText()).isEqualTo("CLARIFICATION");
        assertThat(asked.get(7).path("content").asText()).contains("문자는 대략 몇 건");

        waitCompleted(send(sessionId, "50건"));

        JsonNode last = history(sessionId).path("result").path("messages");
        assertThat(last.size()).isEqualTo(10);
        assertThat(last.get(9).path("messageType").asText()).isEqualTo("ANSWER");
    }

    @Test
    void 데이터_사용량을_받은_뒤에야_알_수_있는_통화_질문도_되묻기를_마친_턴에_처음_한_번_묻는다() throws Exception {
        long sessionId = createSession();

        waitCompleted(send(sessionId, QUESTION));
        waitCompleted(send(sessionId, "만 19~64세"));
        waitCompleted(send(sessionId, "2GB"));

        JsonNode asked = history(sessionId).path("result").path("messages");
        assertThat(asked.size()).isEqualTo(6);
        assertThat(asked.get(5).path("messageType").asText()).isEqualTo("CLARIFICATION");
        assertThat(asked.get(5).path("content").asText()).contains("통화는 대략 몇 분");

        waitCompleted(send(sessionId, "100분"));

        JsonNode last = history(sessionId).path("result").path("messages");
        assertThat(last.size()).isEqualTo(8);
        assertThat(last.get(7).path("messageType").asText()).isEqualTo("ANSWER");
        assertThat(last.get(7).path("content").asText()).isEqualTo("추천 답변: 만 19~64세/2GB");
    }

    @Test
    void 사용량을_묻는_중_나이를_고치면_나이를_바꾸고_사용량을_다시_묻는다() throws Exception {
        long sessionId = createSession();

        waitCompleted(send(sessionId, QUESTION));
        waitCompleted(send(sessionId, "만 19~64세"));
        waitCompleted(send(sessionId, CORRECTION));

        assertThat(states.load(sessionId, requestId.get()).conditions().get("age_group"))
                .isEqualTo(Condition.filled(CORRECTION));
        assertThat(states.findPendingClarificationMessageId(sessionId, requestId.get(), "monthly_data_usage"))
                .isPresent();
        JsonNode asked = history(sessionId).path("result").path("messages");
        assertThat(asked.size()).isEqualTo(6);
        assertThat(asked.get(5).path("messageType").asText()).isEqualTo("CLARIFICATION");
        assertThat(asked.get(5).path("content").asText()).contains("한 달에 데이터를 얼마나 쓰세요");
        // 바꾼 나이대를 알려 같은 질문이 그냥 반복돼 보이지 않게 한다
        assertThat(asked.get(5).path("content").asText()).startsWith("나이대를 만 14~18세로 바꿨어요.");

        waitCompleted(send(sessionId, "40GB"));

        JsonNode last = history(sessionId).path("result").path("messages");
        assertThat(last.get(last.size() - 1).path("messageType").asText()).isEqualTo("ANSWER");
        assertThat(last.get(last.size() - 1).path("content").asText()).isEqualTo("추천 답변: " + CORRECTION + "/40GB");
    }

    private AnalyzedTurn analyze(ChatProcessingCommand command) {
        if (requestId.get() == 0) {
            long id = jdbc.queryForObject("INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,"
                    + "intent,query_text) VALUES (?,?,1,'FAQ',?) RETURNING consult_request_id", Long.class,
                    command.sessionId(), command.inputMessageId(), QUESTION);
            requestId.set(id);
            return new AnalyzedTurn(turns.prepareAnalysis(command.sessionId(),
                    new IntentSubQueryResponse(id, (short) 1, ConsultRequest.Intent.FAQ, QUESTION, Map.of()),
                    LocationStatus.MISSING), null, Purpose.GENERAL_FAQ, command.content(), QUESTION);
        }
        var context = contexts.loadVerified(command);
        var candidate = context.candidates().getFirst();
        // 라우팅이 나이 정정으로 읽은 결과를 분석기(ConsultTurnAnalysisAdapter)와 같은 경로로 넘긴다
        if (CORRECTION.equals(command.content())) {
            var resolution = new com.telme.consult.converter.FollowupConditionConverter().resolve(
                    context, candidate.consultRequestId(), Map.of("age_group", command.content()));
            var correction = turns.prepareWaitingUpdate(context, resolution, LocationStatus.MISSING);
            return new AnalyzedTurn(correction.preparation(), null, correction.purpose(),
                    correction.originalUserQuery(), correction.searchQuery());
        }
        var followup = turns.prepareFollowup(context, new Selection(candidate.consultRequestId(),
                candidate.questionMessageId(), candidate.field(), Map.of(candidate.field(), Condition.filled(command.content()))),
                LocationStatus.MISSING);
        return new AnalyzedTurn(followup.preparation(), followup.followup().answeredField(), followup.purpose(),
                followup.originalUserQuery(), followup.searchQuery());
    }

    private long createSession() throws Exception {
        var result = mvc.perform(post("/api/v1/chat/sessions").session(identity).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"추천\"}")).andExpect(status().isCreated()).andReturn();
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
