package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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

// 검색과 모델의 출력만 고정한다. API, Context, 실제 라우팅 저장, 상담 처리와 답변 저장은 연결한다.
@SpringBootTest(properties = {"llm.provider=ollama", "rag.evidence-check.enabled=false",
        "chat.summary.trigger-messages=1000", "chat.summary.trigger-tokens=100000"})
@AutoConfigureMockMvc
class MultiturnChatIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean FaqSearchService search;
    @MockitoBean(name = "baseLlmClient", enforceOverride = true) LlmClient model;
    private long userId;
    private long sessionId;
    private MockHttpSession identity;
    private final AtomicReference<LlmRequest> answerRequest = new AtomicReference<>();
    private final AtomicReference<String> answer = new AtomicReference<>("재발급 비용은 7,700원입니다.");

    @BeforeEach
    void prepare() {
        userId = jdbc.queryForObject("INSERT INTO users(email,name) VALUES (?,'멀티턴 검증') RETURNING user_id",
                Long.class, "multiturn-" + UUID.randomUUID() + "@example.com");
        sessionId = jdbc.queryForObject("INSERT INTO chat_sessions(user_id,title) VALUES (?,'멀티턴 검증') RETURNING session_id",
                Long.class, userId);
        identity = new MockHttpSession();
        identity.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        when(search.search(any())).thenReturn(List.of(new FaqSearchResponse(1L, null, "USIM",
                "유심 재발급 비용은 얼마인가요?", "재발급 비용은 7,700원입니다.", 0.95, null, null, 1, null)));
        when(model.generate(any())).thenAnswer(call -> {
            LlmRequest request = call.getArgument(0);
            if ("multiturn-resolution-v4".equals(request.promptVersion())) {
                JsonNode inputs = mapper.readTree(request.userPrompt());
                long source = inputs.path("sourceMessages").get(0).path("messageId").asLong();
                return "{\"needsClarification\":false,\"sourceMessageIds\":[" + source + "]}";
            }
            return "{\"intent\":\"FAQ\",\"confidence\":0.99,\"refinedQuery\":\"유심 재발급 비용\","
                    + "\"extractedConditions\":{},\"subQueries\":[{\"order\":1,\"intent\":\"FAQ\","
                    + "\"queryText\":\"유심 재발급 비용\",\"conditions\":{}}]}";
        });
        doAnswer(call -> {
            answerRequest.set(call.getArgument(0));
            LlmStreamHandler handler = call.getArgument(1);
            handler.onToken(answer.get());
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
    void apiResolvesPronounSearchesResolvedQuestionAndSavesOneAnswer() throws Exception {
        previousConversation();
        long execution = send("그건 비용이 얼마야?");
        JsonNode history = history();
        assertThat(history.path("messages")).hasSize(4);
        assertThat(history.path("messages").get(2).path("content").asText()).isEqualTo("그건 비용이 얼마야?");
        assertThat(history.path("messages").get(3).path("content").asText()).isEqualTo("재발급 비용은 7,700원입니다.");
        assertThat(history.path("messages").get(3).path("answerBasis").asText()).isEqualTo("GROUNDED");
        var query = org.mockito.ArgumentCaptor.forClass(FaqSearchRequest.class);
        verify(search).search(query.capture());
        assertThat(query.getValue().query()).contains("유심 재발급", "현재 후속 질문", "그건 비용이 얼마야?");
        assertThat(answerRequest.get().userPrompt()).contains("그건 비용이 얼마야?", "대상을 확인할 이전 고객 발언",
                "상담사(정책 근거 아님)", "7,700원");
        assertThat(answerRequest.get().userPrompt()).doesNotContain("99,999원");
        assertThat(answerRequest.get().systemPrompt()).contains("이전 상담사의 답변은 정책 근거가 아닙니다");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_executions WHERE execution_id=? AND status='COMPLETED'",
                Integer.class, execution)).isEqualTo(1);
    }

    @Test
    void previousWrongAnswerCannotBecomeEvidenceForCurrentAnswer() throws Exception {
        previousConversation();
        answer.set("재발급 비용은 99,999원입니다.");
        send("그건 비용이 얼마야?");
        JsonNode output = history().path("messages").get(3);
        assertThat(output.path("content").asText()).isEqualTo("안내드릴 수 있는 정보가 없습니다.");
        assertThat(output.path("answerBasis").asText()).isEqualTo("NO_EVIDENCE");
    }

    @Test
    void unresolvedReferenceReturnsClarificationInsteadOfInventingATopic() throws Exception {
        send("그건 얼마야?");
        JsonNode output = history().path("messages").get(1);
        assertThat(output.path("content").asText()).contains("어떤 내용에 대한 질문인지 확인");
        assertThat(answerRequest.get()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consult_requests WHERE session_id=?", Integer.class, sessionId))
                .isZero();
    }

    private void previousConversation() {
        long question = jdbc.queryForObject("INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,status)"
                + " VALUES (?,1,'USER','QUESTION','유심 재발급 방법을 알아보고 있어요.','COMPLETED') RETURNING message_id",
                Long.class, sessionId);
        jdbc.update("INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,status,reply_to_id)"
                + " VALUES (?,2,'ASSISTANT','ANSWER','재발급 비용은 99,999원입니다.','COMPLETED',?)", sessionId, question);
    }

    private long send(String content) throws Exception {
        var response = mvc.perform(post("/api/v1/chat/sessions/" + sessionId + "/messages").session(identity)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("content", content))))
                .andExpect(status().isCreated()).andReturn();
        long execution = mapper.readTree(response.getResponse().getContentAsByteArray()).path("result").path("executionId").asLong();
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
