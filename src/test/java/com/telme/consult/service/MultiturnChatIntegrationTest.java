package com.telme.consult.service;

import static com.telme.rag.service.AnswerPromptTemplates.NO_EVIDENCE_ANSWER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.faq.dto.req.FaqSearchKind;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.intent.service.RoutingPromptTemplates;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
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
            if ("context-v31-reference".equals(request.promptVersion())) {
                String question = mapper.readTree(request.userPrompt()).path("currentQuestion").asText();
                // 업무 이름(유심 재발급, 가입)이 있는 질문은 대상이 있다고 판정한다.
                boolean explicit = question.contains("유심") || question.contains("가입");
                return mapper.writeValueAsString(Map.of("reference", explicit ? "EXPLICIT" : "OMITTED"));
            }
            if ("context-v30-sources".equals(request.promptVersion())) {
                return "{\"sources\":[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}],\"anchorIndex\":1,\"referenceScope\":\"RECENT\"}";
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
    @DisplayName("대명사 질문은 복원 문맥으로 검색하고 원질문·출처·단일 답변을 보존한다")
    void 대명사_질문은_복원_문맥으로_검색하고_원문과_출처를_보존한다() throws Exception {
        previousConversation();
        long execution = send("그건 비용이 얼마야?");
        JsonNode history = history();
        assertThat(history.path("messages")).hasSize(4);
        assertThat(history.path("messages").get(2).path("content").asText()).isEqualTo("그건 비용이 얼마야?");
        assertThat(history.path("messages").get(3).path("content").asText()).isEqualTo("재발급 비용은 7,700원입니다.");
        assertThat(history.path("messages").get(3).path("answerBasis").asText()).isEqualTo("GROUNDED");
        var query = ArgumentCaptor.forClass(FaqSearchRequest.class);
        verify(search).search(query.capture());
        String resolvedQuery = """
                [대상을 확인할 이전 고객 발언]
                유심 재발급 방법을 알아보고 있어요.
                [현재 후속 질문]
                그건 비용이 얼마야?
                """.strip();
        assertThat(query.getValue().query()).isEqualTo(resolvedQuery);
        assertThat(query.getValue().kind()).isEqualTo(FaqSearchKind.RESOLVED);
        assertThat(answerRequest.get().userPrompt()).contains("그건 비용이 얼마야?", "유심 재발급 방법을 알아보고 있어요.",
                "7,700원");
        assertThat(answerRequest.get().userPrompt()).doesNotContain("상담사(정책 근거 아님)");
        assertThat(answerRequest.get().userPrompt()).doesNotContain("99,999원");
        assertThat(answerRequest.get().systemPrompt()).contains("이전 상담사의 답변은 정책 근거가 아닙니다");
        var trace = mapper.readTree(jdbc.queryForObject(
                "SELECT pipeline_trace::text FROM chat_executions WHERE execution_id=?", String.class, execution));
        assertThat(trace.path("questionResolution").path("relation").asText()).isEqualTo("HISTORY_DEPENDENT");
        JsonNode sources = trace.path("questionResolution").path("sourceMessageIds");
        assertThat(sources).hasSize(1);
        assertThat(sources.get(0).asLong())
                .isEqualTo(history.path("messages").get(0).path("messageId").asLong());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_executions WHERE execution_id=? AND status='COMPLETED'",
                Integer.class, execution)).isEqualTo(1);
    }

    @Test
    void independentBillingQuestionWithoutHistoryCreatesACompletedConsultation() throws Exception {
        String question = "요금 안 내면 언제 정지되나요?";
        doReturn("{\"reference\":\"EXPLICIT\"}").when(model)
                .generate(argThat(request -> request != null
                        && "context-v31-reference".equals(request.promptVersion())));
        answer.set("미납 요금 확인은 고객센터에서 가능합니다.");
        when(search.search(any())).thenReturn(List.of(new FaqSearchResponse(1L, null, "BILLING",
                question, answer.get(), 0.95, null, null, 1, null)));
        long execution = send(question);
        assertThat(history().path("messages")).hasSize(2);
        assertThat(history().path("messages").get(0).path("content").asText()).isEqualTo(question);
        assertThat(history().path("messages").get(1).path("content").asText()).isEqualTo(answer.get());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consult_requests WHERE session_id=? AND status='DONE'",
                Integer.class, sessionId)).isEqualTo(1);
        var trace = mapper.readTree(jdbc.queryForObject("SELECT pipeline_trace::text FROM chat_executions WHERE execution_id=?",
                String.class, execution));
        assertThat(trace.path("questionResolution").path("relation").asText()).isEqualTo("SELF_CONTAINED");
        assertThat(trace.path("questionResolution").path("sourceMessageIds")).isEmpty();
    }

    @Test
    void realRoutingKeepsRewriteForEmptyOriginalSearchAndRagReceivesOriginal() throws Exception {
        String original = "유심을 잃어버렸어요";
        String refined = "유심 분실 처리 안내";
        String supported = "고객센터나 홈페이지에서 분실 신고하시면 즉시 이용 정지됩니다.";
        scriptSingleFaqRewrite(refined, false);
        answer.set(supported);
        when(search.search(any())).thenAnswer(call -> {
            FaqSearchRequest request = call.getArgument(0);
            return request.query().equals(original) ? List.of() : List.of(new FaqSearchResponse(
                    1L, null, "USIM", "유심 분실 신고는 어디로 하나요?", supported,
                    0.95, 1, null, 1, null));
        });

        long execution = send(original);

        var queries = ArgumentCaptor.forClass(FaqSearchRequest.class);
        verify(search, times(2)).search(queries.capture());
        assertThat(queries.getAllValues()).extracting(FaqSearchRequest::query).containsExactly(original, refined);
        assertThat(queries.getAllValues()).extracting(FaqSearchRequest::kind)
                .containsExactly(FaqSearchKind.ORIGINAL, FaqSearchKind.REFINED);
        assertThat(meaningChecks).hasValue(1);
        assertThat(history().path("messages").get(0).path("content").asText()).isEqualTo(original);
        assertThat(history().path("messages").get(1).path("content").asText()).isEqualTo(supported);
        assertThat(answerRequest.get().userPrompt()).contains(original);
        assertThat(jdbc.queryForObject("SELECT query_text FROM consult_requests WHERE session_id=?",
                String.class, sessionId)).isEqualTo(refined);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consult_requests WHERE session_id=? AND status='DONE'",
                Integer.class, sessionId)).isEqualTo(1);
        JsonNode trace = mapper.readTree(jdbc.queryForObject(
                "SELECT pipeline_trace::text FROM chat_executions WHERE execution_id=?", String.class, execution));
        assertThat(trace.path("searchResults").get(0).path("status").asText()).isEqualTo("EMPTY");
        assertThat(trace.path("searchResults").get(1).path("status").asText()).isEqualTo("FOUND");
    }

    @Test
    void successfulOriginalSearchSkipsRewriteAndItsMeaningValidation() throws Exception {
        scriptSingleFaqRewrite("유심 재발급 비용", false);

        send("유심 새로 받는 데 얼마인가요?");

        var query = ArgumentCaptor.forClass(FaqSearchRequest.class);
        verify(search).search(query.capture());
        assertThat(query.getValue().query()).isEqualTo("유심 새로 받는 데 얼마인가요?");
        // 원문 검색이 성공하면 검색 문장의 의미 검증 모델을 부르지 않는다.
        assertThat(meaningChecks).hasValue(0);
        assertThat(history().path("messages").get(1).path("answerBasis").asText()).isEqualTo("GROUNDED");
    }

    @Test
    void unsafeRewriteIsNotSearchedEvenWhenOriginalHasNoResults() throws Exception {
        scriptSingleFaqRewrite("유심 분실 시 재발급 방법", true);
        when(search.search(any())).thenReturn(List.of());

        send("유심을 잃어버렸어요");

        var query = ArgumentCaptor.forClass(FaqSearchRequest.class);
        verify(search).search(query.capture());
        assertThat(query.getValue().query()).isEqualTo("유심을 잃어버렸어요");
        assertThat(meaningChecks).hasValue(1);
        assertThat(history().path("messages").get(1).path("answerBasis").asText()).isEqualTo("NO_EVIDENCE");
        assertThat(answerRequest.get()).isNull();
    }

    private final AtomicInteger meaningChecks = new AtomicInteger();

    private void scriptSingleFaqRewrite(String refined, boolean unsafe) {
        doAnswer(call -> {
            LlmRequest request = call.getArgument(0);
            if (RoutingPromptTemplates.FAQ_QUERY_FAITHFULNESS_PROMPT.equals(request.systemPrompt())) {
                meaningChecks.incrementAndGet();
                return mapper.writeValueAsString(Map.of("unsafe", List.of(unsafe)));
            }
            return mapper.writeValueAsString(Map.of("intent", "FAQ", "confidence", 0.99,
                    "refinedQuery", refined, "subQueries", List.of(Map.of("order", 1,
                            "intent", "FAQ", "queryText", refined))));
        }).when(model).generate(any());
    }

    @Test
    void selfContainedTemporalQuestionWithoutHistoryContinuesToSearchAndAnswer() throws Exception {
        String question = "이전에 신청한 유심 재발급 비용은 얼마인가요?";
        send(question);
        JsonNode output = history().path("messages").get(1);
        assertThat(output.path("content").asText()).isEqualTo("재발급 비용은 7,700원입니다.");
        assertThat(output.path("answerBasis").asText()).isEqualTo("GROUNDED");
        verify(search).search(any());
        assertThat(answerRequest.get().userPrompt()).contains(question).doesNotContain("conversation_data");
    }

    @Test
    void previousWrongAnswerCannotBecomeEvidenceForCurrentAnswer() throws Exception {
        previousConversation();
        answer.set("재발급 비용은 99,999원입니다.");
        send("그건 비용이 얼마야?");
        JsonNode output = history().path("messages").get(3);
        assertThat(output.path("content").asText()).isEqualTo(NO_EVIDENCE_ANSWER);
        assertThat(output.path("answerBasis").asText()).isEqualTo("NO_EVIDENCE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"그건 얼마야?", "그때 요금은 얼마야?", "앞서 말한 건 얼마야?",
            "이전에 물어본 거 얼마야?", "아까 얼마랬죠?"})
    void unresolvedReferenceReturnsClarificationInsteadOfInventingATopic(String question) throws Exception {
        send(question);
        JsonNode output = history().path("messages").get(1);
        assertThat(output.path("content").asText()).contains("어떤 내용에 대한 질문인지 확인");
        assertThat(answerRequest.get()).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consult_requests WHERE session_id=?", Integer.class, sessionId))
                .isZero();
    }

    @Test
    void independentEnrollmentQuestionDoesNotInheritRoamingTopicInRagPrompt() throws Exception {
        previousConversation("로밍 요금제는 어떻게 골라요?");
        answer.set("만 14세 미만은 가입할 수 없습니다.");
        when(search.search(any())).thenReturn(List.of(new FaqSearchResponse(1L, null, "SUBSCRIBE",
                "미성년자도 가입할 수 있나요?", answer.get(), 0.95, null, null, 1, null)));
        send("미성년자도 가입 되나요");
        assertThat(answerRequest.get().userPrompt()).contains("미성년자도 가입 되나요");
        assertThat(answerRequest.get().userPrompt()).doesNotContain("로밍", "99,999원", "conversation_data");
        assertThat(history().path("messages").get(3).path("content").asText()).isEqualTo(answer.get());
    }

    private void previousConversation() {
        previousConversation("유심 재발급 방법을 알아보고 있어요.");
    }

    private void previousConversation(String content) {
        long question = jdbc.queryForObject("INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,status)"
                + " VALUES (?,1,'USER','QUESTION',?,'COMPLETED') RETURNING message_id",
                Long.class, sessionId, content);
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
