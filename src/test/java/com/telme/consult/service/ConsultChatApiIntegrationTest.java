package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ChatProcessingPort;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.JdbcConsultStateStore;
import com.telme.consult.service.FollowupSelectionValidator.Selection;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.ContextProvider;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;

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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** 분석만 목업으로 넣고 실제 Chat 요청·비동기 전달·상담 저장을 확인한다. */
@SpringBootTest(properties = {
        "telme.consult.persistence-enabled=true",
        "spring.datasource.hikari.maximum-pool-size=2"
})
@AutoConfigureMockMvc(addFilters = false)
class ConsultChatApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsultService consult;
    @Autowired JdbcConsultStateStore states;
    @Autowired ConsultChatPersistenceService persistence;
    @Autowired ConsultTurnPreparationService turns;
    @Autowired ContextProvider contexts;
    @MockitoBean ChatProcessingPort processing;
    long userId;
    MockHttpSession identity;
    AtomicLong requestId = new AtomicLong();
    AtomicReference<ConsultChatProcessingService.AnswerInput> answerInput =
            new AtomicReference<>();
    AtomicReference<RuntimeException> answerFailure = new AtomicReference<>();

    @BeforeEach
    void setup() {
        userId =
                jdbc.queryForObject(
                        "INSERT INTO users(email,name) VALUES (?,'API 검증') RETURNING user_id",
                        Long.class,
                        "consult-api-" + UUID.randomUUID() + "@example.com");
        identity = new MockHttpSession();
        identity.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        var actualProcessing =
                new ConsultChatProcessingService(
                        this::analyze,
                        input -> {
                            if (answerFailure.get() != null) {
                                throw answerFailure.get();
                            }
                            answerInput.set(input);
                            return ConsultChatProcessingService.GeneratedAnswer.withoutSources(
                                    new ChatAnswer(
                                            ChatMessage.MessageType.ANSWER,
                                            "조건에 맞는 매장을 안내해드릴게요.",
                                            null,
                                            List.of(),
                                            null));
                        },
                        persistence,
                        new ConfirmedConditionConverter());
        doAnswer(
                        invocation -> {
                            actualProcessing.request(invocation.getArgument(0));
                            return null;
                        })
                .when(processing)
                .request(any());
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
    void chatApiCreatesClarificationAndResumesSameConsultation() throws Exception {
        var created =
                mvc.perform(
                                post("/api/v1/chat/sessions")
                                        .session(identity)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"title\":\"상담 연결\"}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        long sid =
                mapper.readTree(created.getResponse().getContentAsByteArray())
                        .path("result")
                        .path("sessionId")
                        .asLong();
        long firstExecution = send(sid, "매장 알려줘");
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.queryForObject(
                                                        "SELECT status FROM chat_executions WHERE"
                                                                + " execution_id=?",
                                                        String.class,
                                                        firstExecution))
                                        .isEqualTo("COMPLETED"));
        var firstHistory = history(sid);
        assertThat(firstHistory.path("result").path("messages").size()).isEqualTo(2);
        assertThat(firstHistory.path("result").path("messages").get(1).path("messageType").asText())
                .isEqualTo("CLARIFICATION");
        long followupExecution = send(sid, "강남역이요");
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                assertThat(
                                                states.load(sid, requestId.get())
                                                        .conditions()
                                                        .get("location"))
                                        .isEqualTo(Condition.filled("강남역")));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM consult_requests WHERE session_id=?",
                                Integer.class,
                                sid))
                .isEqualTo(1);
        waitCompleted(followupExecution);
        assertThat(states.load(sid, requestId.get()).status()).isEqualTo("DONE");
        var finalHistory = history(sid);
        assertThat(finalHistory.path("result").path("messages").size()).isEqualTo(4);
        assertThat(finalHistory.path("result").path("messages").get(3).path("messageType").asText())
                .isEqualTo("ANSWER");
        assertThat(answerInput.get().originalUserQuery()).isEqualTo("매장 알려줘");
        assertThat(answerInput.get().searchQuery()).isEqualTo("매장");
        assertThat(answerInput.get().confirmedConditions())
                .containsExactlyInAnyOrderEntriesOf(Map.of("location", "강남역"));
    }

    @Test
    void correctionWhileWaitingAllowsNextChatMessage() throws Exception {
        long sid = createSession();
        waitCompleted(send(sid, "매장 알려줘"));
        long pendingId =
                states.findPendingClarificationMessageId(sid, requestId.get(), "location")
                        .orElseThrow();
        waitCompleted(send(sid, "유심 재발급으로 바꿀게요"));
        assertThat(states.load(sid, requestId.get()).conditions().get("serviceType"))
                .isEqualTo(Condition.filled("USIM_REISSUE"));
        assertThat(states.findPendingClarificationMessageId(sid, requestId.get(), "location"))
                .contains(pendingId);
        assertThat(history(sid).path("result").path("messages").size()).isEqualTo(3);
        long finalExecution = send(sid, "강남역이요");
        waitCompleted(finalExecution);
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                assertThat(
                                                states.load(sid, requestId.get())
                                                        .conditions()
                                                        .get("location"))
                                        .isEqualTo(Condition.filled("강남역")));
        assertThat(states.load(sid, requestId.get()).conditions().get("serviceType"))
                .isEqualTo(Condition.filled("USIM_REISSUE"));
        assertThat(states.load(sid, requestId.get()).status()).isEqualTo("DONE");
        assertThat(history(sid).path("result").path("messages").size()).isEqualTo(5);
        assertThat(answerInput.get().originalUserQuery()).isEqualTo("매장 알려줘");
        assertThat(answerInput.get().searchQuery()).isEqualTo("매장");
        assertThat(answerInput.get().confirmedConditions())
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("location", "강남역", "serviceType", "USIM_REISSUE"));
    }

    @Test
    void unchangedWaitingAlsoAllowsNextChatMessage() throws Exception {
        long sid = createSession();
        waitCompleted(send(sid, "매장 알려줘"));
        int version = states.load(sid, requestId.get()).version();
        waitCompleted(send(sid, "잠깐만요"));
        assertThat(states.load(sid, requestId.get()).version()).isEqualTo(version);
        assertThat(history(sid).path("result").path("messages").size()).isEqualTo(3);
        long finalExecution = send(sid, "강남역이요");
        waitCompleted(finalExecution);
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                assertThat(
                                                states.load(sid, requestId.get())
                                                        .conditions()
                                                        .get("location"))
                                        .isEqualTo(Condition.filled("강남역")));
        assertThat(states.load(sid, requestId.get()).status()).isEqualTo("DONE");
    }

    @Test
    void answerFailureMarksExecutionFailedWithoutCompletingConsultation() throws Exception {
        long sid = createSession();
        waitCompleted(send(sid, "매장 알려줘"));
        answerFailure.set(new IllegalStateException("검색 실패"));

        long failedExecution = send(sid, "강남역이요");

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.queryForObject(
                                                        "SELECT status FROM chat_executions WHERE"
                                                                + " execution_id=?",
                                                        String.class,
                                                        failedExecution))
                                        .isEqualTo("FAILED"));
        assertThat(states.load(sid, requestId.get()).status()).isNotEqualTo("DONE");
        var failedHistory = history(sid).path("result").path("messages");
        assertThat(failedHistory.size()).isEqualTo(4);
        assertThat(failedHistory.get(3).path("messageType").asText()).isEqualTo("ANSWER");
        assertThat(failedHistory.get(3).path("status").asText()).isEqualTo("FAILED");
    }

    private long createSession() throws Exception {
        var result =
                mvc.perform(
                                post("/api/v1/chat/sessions")
                                        .session(identity)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"title\":\"대기 처리\"}"))
                        .andExpect(status().isCreated())
                        .andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray())
                .path("result")
                .path("sessionId")
                .asLong();
    }

    private ConsultChatProcessingService.AnalyzedTurn analyze(ChatProcessingCommand command) {
        if (requestId.get() == 0) {
            long id =
                    jdbc.queryForObject(
                            "INSERT INTO"
                                + " consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)"
                                + " VALUES (?,?,1,'STORE','매장') RETURNING consult_request_id",
                            Long.class,
                            command.sessionId(),
                            command.inputMessageId());
            requestId.set(id);
            return new ConsultChatProcessingService.AnalyzedTurn(
                    turns.prepareAnalysis(
                            command.sessionId(),
                            new IntentSubQueryResponse(
                                    id,
                                    (short) 1,
                                    ConsultRequest.Intent.STORE,
                                    "매장",
                                    Map.of()),
                            LocationStatus.MISSING),
                    null,
                    Purpose.NEARBY_STORE,
                    command.content(),
                    "매장");
        }
        if ("유심 재발급으로 바꿀게요".equals(command.content())
                || "잠깐만요".equals(command.content())) {
            Map<String, Condition> updates =
                    "잠깐만요".equals(command.content())
                            ? Map.of()
                            : Map.of("serviceType", Condition.filled("USIM_REISSUE"));
            return new ConsultChatProcessingService.AnalyzedTurn(
                    consult.prepareTurn(
                            command.sessionId(),
                            requestId.get(),
                            Purpose.NEARBY_STORE,
                            updates,
                            LocationStatus.MISSING),
                    null,
                    Purpose.NEARBY_STORE,
                    "매장 알려줘",
                    "매장");
        }
        var context = contexts.loadVerified(command);
        var candidate = context.candidates().getFirst();
        var followup =
                turns.prepareFollowup(
                        context,
                        new Selection(
                                candidate.consultRequestId(),
                                candidate.questionMessageId(),
                                "location",
                                Map.of("location", Condition.filled("강남역"))),
                        LocationStatus.MISSING);
        return new ConsultChatProcessingService.AnalyzedTurn(
                followup.preparation(),
                followup.followup().answeredField(),
                followup.purpose(),
                followup.originalUserQuery(),
                followup.searchQuery());
    }

    private void waitCompleted(long executionId) {
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(
                        () ->
                                assertThat(
                                                jdbc.queryForObject(
                                                        "SELECT status FROM chat_executions WHERE"
                                                            + " execution_id=?",
                                                        String.class,
                                                        executionId))
                                        .isEqualTo("COMPLETED"));
    }

    private long send(long sid, String content) throws Exception {
        var result =
                mvc.perform(
                                post("/api/v1/chat/sessions/{sessionId}/messages", sid)
                                        .session(identity)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                mapper.writeValueAsString(
                                                        Map.of("content", content))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray())
                .path("result")
                .path("executionId")
                .asLong();
    }

    private com.fasterxml.jackson.databind.JsonNode history(long sid) throws Exception {
        var result =
                mvc.perform(
                                get("/api/v1/chat/sessions/{sessionId}/messages", sid)
                                        .session(identity))
                        .andExpect(status().isOk())
                        .andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
