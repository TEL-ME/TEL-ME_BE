package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatExecutionState;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ChatProcessingPort;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.JdbcConsultStateStore;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.intent.dto.res.FollowUpRouteResponse;
import com.telme.intent.dto.res.FollowUpRouteResponse.Disposition;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;
import com.telme.intent.entity.QueryRouting.Intent;
import com.telme.intent.entity.QueryRouting.Method;
import com.telme.intent.service.QueryRoutingService;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 라우팅·검색·모델만 대체하고 활성 분석기·처리기·저장·이력 API·SSE를 연결한다. */
@SpringBootTest(
        properties = {
            "telme.consult.persistence-enabled=true",
            "telme.consult.chat-integration-enabled=true",
            "telme.consult.rag-integration-enabled=true",
            "telme.consult.llm-enabled=false",
            "llm.provider=ollama",
            "rag.evidence-check.enabled=false",
            "spring.datasource.hikari.maximum-pool-size=2"
        })
@AutoConfigureMockMvc
class BasicConversationChatIntegrationTest {
    @Autowired ChatProcessingPort processor;
    @Autowired ChatEmitterRegistry emitters;
    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcConsultStateStore states;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean QueryRoutingService routing;
    @MockitoBean FaqSearchService search;

    @MockitoBean(name = "baseLlmClient", enforceOverride = true)
    LlmClient model;

    private long userId;
    private long sessionId;
    private long requestId;
    private MockHttpSession identity;

    @BeforeEach
    void 준비한다() {
        userId =
                jdbc.queryForObject(
                        "INSERT INTO users(email,name) VALUES (?,'기본 대화 검증') RETURNING user_id",
                        Long.class,
                        "basic-chat-" + UUID.randomUUID() + "@example.com");
        sessionId =
                jdbc.queryForObject(
                        "INSERT INTO chat_sessions(user_id,title) VALUES (?,'기본 대화') RETURNING"
                            + " session_id",
                        Long.class,
                        userId);
        identity = new MockHttpSession();
        identity.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
    }

    @AfterEach
    void 정리한다() {
        jdbc.update(
                "DELETE FROM consult_conditions WHERE consult_request_id IN"
                        + " (SELECT consult_request_id FROM consult_requests WHERE session_id=?)",
                sessionId);
        jdbc.update("DELETE FROM consult_requests WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM chat_sessions WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM users WHERE user_id=?", userId);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "안녕하세요 | 안녕하세요. 무엇을 도와드릴까요?",
                "감사합니다 | 천만에요. 더 궁금한 점이 있으면 말씀해 주세요.",
                "안녕히 계세요 | 이용해 주셔서 감사합니다. 궁금한 점이 생기면 다시 말씀해 주세요.",
                "도움말 | 요금제, 청구·납부, 유심, 로밍 등 통신 서비스와 매장 관련 질문을 도와드려요. 궁금한 내용을 말씀해 주세요."
            })
    @DisplayName("활성 처리기는 기본 응답을 한 번 저장하고 완료 이벤트와 이력에서 같은 메시지를 반환한다")
    void 기본_응답의_저장_조회_완료_이벤트가_일치한다(String input, String expected) throws Exception {
        assertThat(processor).isInstanceOf(ConsultChatProcessingService.class);
        ChatProcessingCommand command = 입력을_준비한다(input);
        CaptureEmitter emitter = new CaptureEmitter();
        emitters.register(command.executionId(), emitter);

        processor.request(command);

        assertThat(emitter.names).containsExactly("complete");
        assertThat(emitter.completed.status().name()).isEqualTo("COMPLETED");
        JsonNode history = 이력을_조회한다();
        JsonNode output = history.path("messages").get(1);
        assertThat(output.path("messageId").asLong())
                .isEqualTo(emitter.completed.outputMessage().messageId());
        assertThat(output.path("content").asText()).isEqualTo(expected);
        assertThat(output.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(output.path("answerBasis").isNull()).isTrue();
        assertThat(history.path("runningExecutionId").isNull()).isTrue();
        assertThat(이력을_조회한다()).isEqualTo(history);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM chat_messages WHERE session_id=?",
                                Integer.class,
                                sessionId))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM consult_requests WHERE session_id=?",
                                Integer.class,
                                sessionId))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM chat_sessions WHERE session_id=?",
                                String.class,
                                sessionId))
                .isEqualTo("ACTIVE");
        verifyNoInteractions(routing, search, model);
    }

    @Test
    @DisplayName("메시지 접수 API도 기본 대화 처리기로 연결된다")
    void 메시지_접수_API에서_인사를_처리한다() throws Exception {
        var response =
                mvc.perform(
                                post("/api/v1/chat/sessions/" + sessionId + "/messages")
                                        .session(identity)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                mapper.writeValueAsString(
                                                        Map.of("content", "안녕하세요"))))
                        .andExpect(status().isCreated())
                        .andReturn();
        long executionId =
                mapper.readTree(response.getResponse().getContentAsByteArray())
                        .path("result")
                        .path("executionId")
                        .asLong();
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

        assertThat(이력을_조회한다().path("messages").get(1).path("content").asText())
                .isEqualTo("안녕하세요. 무엇을 도와드릴까요?");
        verifyNoInteractions(routing, search, model);
    }

    @Test
    @DisplayName("인사가 섞인 FAQ 질문은 기존 검색·생성·Guard 경로에서 답변한다")
    void 인사가_섞여도_실제_질문에_답한다() throws Exception {
        when(routing.routeSingleConsult(any(), any()))
                .thenAnswer(
                        invocation -> {
                            ChatMessage message = invocation.getArgument(0);
                            return 라우팅_결과(message, Intent.FAQ);
                        });
        when(search.search(any()))
                .thenReturn(
                        List.of(
                                new FaqSearchResponse(
                                        9999999L,
                                        null,
                                        "USIM",
                                        "유심 재발급 비용은 얼마인가요?",
                                        "유심 재발급 비용은 7,700원입니다.",
                                        0.9,
                                        1,
                                        LocalDate.of(2026, 9, 17),
                                        1,
                                        null)));
        doAnswer(
                invocation -> {
                    LlmStreamHandler handler = invocation.getArgument(1);
                    handler.onToken("유심 재발급 비용은 7,700원입니다.");
                    handler.onComplete();
                    return null;
                })
                .when(model)
                .stream(any(), any());
        ChatProcessingCommand command = 입력을_준비한다("안녕하세요, 유심 재발급 비용 알려주세요");

        processor.request(command);

        assertThat(이력을_조회한다().path("messages").get(1).path("content").asText())
                .isEqualTo("유심 재발급 비용은 7,700원입니다.");
        assertThat(이력을_조회한다().path("messages").get(1).path("answerBasis").asText())
                .isEqualTo("GROUNDED");
        verify(routing).routeSingleConsult(any(), any());
        verify(search).search(any());
        verify(model).stream(any(), any());
    }

    @Test
    @DisplayName("대기 중 감사는 기존 보류 처리로 넘기고 원래 지역 질문에 후속 답변을 연결한다")
    void 대기_중_감사가_상담_조건을_지우지_않는다() throws Exception {
        when(routing.routeSingleConsult(any(), any()))
                .thenAnswer(invocation -> 라우팅_결과(invocation.getArgument(0), Intent.STORE));
        processor.request(입력을_준비한다("매장 알려줘"));
        var before = states.load(sessionId, requestId);
        long questionId =
                states.findPendingClarificationMessageId(sessionId, requestId, "location")
                        .orElseThrow();
        when(routing.analyzeFollowUp(sessionId, "감사합니다"))
                .thenReturn(
                        new FollowUpRouteResponse(
                                requestId, Map.of(), Set.of(), Method.RULE, Disposition.DEFERRED));

        processor.request(입력을_준비한다("감사합니다"));

        assertThat(states.load(sessionId, requestId)).isEqualTo(before);
        assertThat(states.findPendingClarificationMessageId(sessionId, requestId, "location"))
                .contains(questionId);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM chat_sessions WHERE session_id=?",
                                String.class,
                                sessionId))
                .isEqualTo("NEED_CLARIFICATION");
        when(routing.analyzeFollowUp(sessionId, "강남역이요, 고마워요"))
                .thenReturn(
                        new FollowUpRouteResponse(
                                requestId,
                                Map.of("location", "강남역", "serviceType", "USIM_REISSUE"),
                                Set.of(), Method.RULE));

        processor.request(입력을_준비한다("강남역이요, 고마워요"));

        assertThat(states.load(sessionId, requestId).conditions().get("location"))
                .isEqualTo(Condition.filled("강남역"));
        assertThat(states.load(sessionId, requestId).status()).isEqualTo("DONE");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM consult_requests WHERE session_id=?",
                                Integer.class,
                                sessionId))
                .isEqualTo(1);
        verify(routing).analyzeFollowUp(sessionId, "감사합니다");
        verify(routing).analyzeFollowUp(sessionId, "강남역이요, 고마워요");
        verifyNoInteractions(search, model);
    }

    private IntentRouteResponse 라우팅_결과(ChatMessage message, Intent intent) {
        String query = intent == Intent.STORE ? "매장" : "유심 재발급 비용";
        requestId =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)"
                            + " VALUES (?,?,1,?,?) RETURNING consult_request_id",
                        Long.class,
                        sessionId,
                        message.getMessageId(),
                        intent.name(),
                        query);
        return new IntentRouteResponse(
                null,
                message.getMessageId(),
                intent,
                query,
                BigDecimal.ONE,
                Method.RULE,
                Map.of(),
                List.of(
                        new IntentSubQueryResponse(
                                requestId,
                                (short) 1,
                                ConsultRequest.Intent.valueOf(intent.name()),
                                query,
                                Map.of())));
    }

    private ChatProcessingCommand 입력을_준비한다(String input) {
        int sequence =
                jdbc.queryForObject(
                        "SELECT coalesce(max(sequence_no),0)+1 FROM chat_messages WHERE"
                            + " session_id=?",
                        Integer.class,
                        sessionId);
        long messageId =
                jdbc.queryForObject(
                        "INSERT INTO"
                            + " chat_messages(session_id,sequence_no,role,message_type,content,status,completed_at)"
                            + " VALUES (?,?,'USER','QUESTION',?,'COMPLETED',now()) RETURNING"
                            + " message_id",
                        Long.class,
                        sessionId,
                        sequence,
                        input);
        long executionId =
                jdbc.queryForObject(
                        "INSERT INTO chat_executions(session_id,input_message_id,status)"
                                + " VALUES (?,?,'RUNNING') RETURNING execution_id",
                        Long.class,
                        sessionId,
                        messageId);
        return new ChatProcessingCommand(executionId, sessionId, messageId, input);
    }

    private JsonNode 이력을_조회한다() throws Exception {
        var response =
                mvc.perform(
                                get("/api/v1/chat/sessions/" + sessionId + "/messages")
                                        .session(identity))
                        .andExpect(status().isOk())
                        .andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray()).path("result");
    }

    private final class CaptureEmitter extends SseEmitter {
        private final List<String> names = new ArrayList<>();
        private ChatExecutionState completed;

        @Override
        public void send(SseEventBuilder builder) {
            for (var item : builder.build()) {
                if (item.getData() instanceof String text && text.startsWith("event:")) {
                    names.add(text.substring(6, text.indexOf('\n')));
                } else if (item.getData() instanceof ChatExecutionState state) {
                    completed = state;
                    assertThat(
                                    jdbc.queryForObject(
                                            "SELECT status FROM chat_executions WHERE"
                                                + " execution_id=?",
                                            String.class,
                                            state.executionId()))
                            .isEqualTo("COMPLETED");
                    assertThat(
                                    jdbc.queryForObject(
                                            "SELECT status FROM chat_messages WHERE message_id=?",
                                            String.class,
                                            state.outputMessage().messageId()))
                            .isEqualTo("COMPLETED");
                }
            }
        }
    }
}
