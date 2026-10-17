package com.telme.chat.translation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

//한글이 없는 질문을 한국어로 바꿔 검색·답변하고, 대화 기록에는 원문을 보여 주는지 API부터 저장까지 연결해 확인한다.
//검색 결과와 모델 출력만 고정한다
@SpringBootTest(properties = {"llm.provider=ollama", "rag.evidence-check.enabled=false",
        "chat.summary.trigger-messages=1000", "chat.summary.trigger-tokens=100000"})
@AutoConfigureMockMvc
class ChatQuestionTranslationIntegrationTest {

    private static final String ENGLISH = "How much does a USIM reissue cost?";
    private static final String KOREAN = "유심 재발급 비용은 얼마인가요?";
    private static final String ANSWER = "재발급 비용은 7,700원입니다.";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean FaqSearchService search;
    @MockitoBean(name = "baseLlmClient", enforceOverride = true) LlmClient model;

    private long userId;
    private long sessionId;
    private MockHttpSession identity;

    @BeforeEach
    void prepare() {
        userId = jdbc.queryForObject("INSERT INTO users(email,name) VALUES (?,'번역 검증') RETURNING user_id",
                Long.class, "translation-" + UUID.randomUUID() + "@example.com");
        sessionId = jdbc.queryForObject("INSERT INTO chat_sessions(user_id,title) VALUES (?,'번역 검증') RETURNING session_id",
                Long.class, userId);
        identity = new MockHttpSession();
        identity.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        when(search.search(any())).thenReturn(List.of(new FaqSearchResponse(1L, null, "USIM",
                KOREAN, ANSWER, 0.95, null, null, 1, null)));
        when(model.generate(any())).thenAnswer(call -> {
            LlmRequest request = call.getArgument(0);
            if (ChatQuestionTranslator.PROMPT_VERSION.equals(request.promptVersion())) {
                return KOREAN;
            }
            if ("context-v31-reference".equals(request.promptVersion())) {
                return "{\"reference\":\"EXPLICIT\"}";
            }
            return "{\"intent\":\"FAQ\",\"confidence\":0.99,\"refinedQuery\":\"유심 재발급 비용\","
                    + "\"extractedConditions\":{},\"subQueries\":[{\"order\":1,\"intent\":\"FAQ\","
                    + "\"queryText\":\"유심 재발급 비용\",\"conditions\":{}}]}";
        });
        doAnswer(call -> {
            LlmStreamHandler handler = call.getArgument(1);
            handler.onToken(ANSWER);
            handler.onComplete();
            return null;
        }).when(model).stream(any(), any());
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM consult_requests WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM chat_sessions WHERE session_id=?", sessionId);
        jdbc.update("DELETE FROM users WHERE user_id=?", userId);
    }

    @Test
    @DisplayName("영어 질문은 한국어로 검색해 답하고, 대화 기록에는 원문을, trace에는 원문과 번역문을 남긴다")
    void 영어_질문을_한국어로_처리한다() throws Exception {
        long execution = send(ENGLISH);

        Map<String, Object> question = jdbc.queryForMap(
                "SELECT content, original_content FROM chat_messages WHERE session_id=? AND role='USER'", sessionId);
        assertThat(question).containsEntry("content", KOREAN).containsEntry("original_content", ENGLISH);
        ArgumentCaptor<FaqSearchRequest> query = ArgumentCaptor.forClass(FaqSearchRequest.class);
        verify(search, atLeastOnce()).search(query.capture());
        assertThat(query.getAllValues().get(0).query()).isEqualTo(KOREAN);

        JsonNode messages = history().path("messages");
        assertThat(messages.get(0).path("content").asText()).isEqualTo(ENGLISH);
        assertThat(messages.get(1).path("content").asText()).isEqualTo(ANSWER);

        JsonNode trace = mapper.readTree(jdbc.queryForObject(
                "SELECT pipeline_trace::text FROM chat_executions WHERE execution_id=?", String.class, execution));
        assertThat(trace.path("questionTranslation").path("outcome").asText()).isEqualTo("TRANSLATED");
        assertThat(trace.path("questionTranslation").path("original").asText()).isEqualTo(ENGLISH);
        assertThat(trace.path("questionTranslation").path("translated").asText()).isEqualTo(KOREAN);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM llm_generations WHERE execution_id=? AND task_type='TRANSLATION'",
                Integer.class, execution)).isEqualTo(1);
    }

    @Test
    @DisplayName("한국어 질문은 번역 모델을 부르지 않고 원문 칸도 비워 둔다")
    void 한국어_질문은_번역하지_않는다() throws Exception {
        send(KOREAN);

        verify(model, never()).generate(argThat(request -> request != null
                && ChatQuestionTranslator.PROMPT_VERSION.equals(request.promptVersion())));
        assertThat(jdbc.queryForObject(
                "SELECT original_content FROM chat_messages WHERE session_id=? AND role='USER'", String.class, sessionId))
                .isNull();
    }

    private long send(String content) throws Exception {
        var response = mvc.perform(post("/api/v1/chat/sessions/" + sessionId + "/messages").session(identity)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("content", content))))
                .andExpect(status().isCreated()).andReturn();
        long execution = mapper.readTree(response.getResponse().getContentAsByteArray())
                .path("result").path("executionId").asLong();
        await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_executions WHERE execution_id=?", String.class, execution)).isEqualTo("COMPLETED"));
        return execution;
    }

    private JsonNode history() throws Exception {
        var response = mvc.perform(get("/api/v1/chat/sessions/" + sessionId + "/messages").session(identity))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray()).path("result");
    }
}
