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
    static final List<String> OPTIONS = List.of("만 14~18세", "만 19~64세", "만 65세 이상");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsultService consult;
    @Autowired JdbcConsultStateStore states;
    @Autowired ConsultChatPersistenceService persistence;
    @Autowired ConsultTurnPreparationService turns;
    @Autowired ContextProvider contexts;
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
        when(classifier.classify(any(), any())).thenReturn(PlanQuestionType.RECOMMENDATION);
        MemberRecommendationAnswerer recommendation = mock(MemberRecommendationAnswerer.class);
        when(recommendation.recommend(any(), any(), anyBoolean())).thenAnswer(call -> {
            Map<String, String> confirmed = call.getArgument(1);
            boolean mayAsk = call.getArgument(2);
            askedWith.set(confirmed.get("age_group") + "/" + confirmed.get("heavy_data"));
            mayAskLast.set(mayAsk);
            if (confirmed.isEmpty() && mayAsk) {
                return new MemberRecommendationAnswerer.Outcome(null, List.of(), List.of(
                        new MissingCondition("age_group", "나이에 따라 추천하는 요금제가 달라져요. 나이대가 어떻게 되세요?", OPTIONS, null),
                        new MissingCondition("heavy_data", "데이터를 많이 쓰시는 편인가요? (한 달 50GB 이상)", List.of("예", "아니요"), null)));
            }
            return new MemberRecommendationAnswerer.Outcome("추천 답변: " + askedWith.get(), List.of(), null);
        });
        var answers = new PlanAnswerProvider(faq, classifier, mock(PlanCatalogAnswerer.class),
                mock(MemberPlanAnswerer.class), recommendation, sessionId -> Optional.of(userId),
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
        assertThat(second.get(3).path("content").asText()).contains("데이터를 많이 쓰시는 편인가요");

        waitCompleted(send(sessionId, "예"));

        assertThat(states.load(sessionId, requestId.get()).conditions().get("age_group"))
                .isEqualTo(Condition.filled("만 65세 이상"));
        assertThat(states.load(sessionId, requestId.get()).conditions().get("heavy_data"))
                .isEqualTo(Condition.filled("예"));
        JsonNode last = history(sessionId).path("result").path("messages");
        assertThat(last.size()).isEqualTo(6);
        assertThat(last.get(5).path("messageType").asText()).isEqualTo("ANSWER");
        assertThat(last.get(5).path("content").asText()).isEqualTo("추천 답변: 만 65세 이상/예");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consult_requests WHERE session_id=?", Integer.class,
                sessionId)).isEqualTo(1);
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
