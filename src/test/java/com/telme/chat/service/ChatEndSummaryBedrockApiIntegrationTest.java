package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.telme.llm.service.LlmClient;
import com.telme.llm.exception.BedrockTemporaryException;
import com.telme.llm.exception.LlmErrorCode;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;

@SpringBootTest(properties = {"chat.end-summary.enabled=true", "chat.end-summary.dispatch-enabled=false",
        "chat.end-summary.scheduler-enabled=false", "chat.end-summary.model.provider=bedrock"})
@AutoConfigureMockMvc
class ChatEndSummaryBedrockApiIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<JsonNode> FALLBACK_REQUESTS = new CopyOnWriteArrayList<>();
    private static final HttpServer OLLAMA = createOllama();
    private static volatile boolean invalidFallback;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ChatSessionEndSummaryService service;
    @MockitoBean(name = "chatEndSummaryBedrockRuntime", enforceOverride = true)
    private BedrockRuntimeClient runtime;
    @MockitoBean private LlmClient primary;

    @DynamicPropertySource
    static void 대체_호출을_로컬_대역에_연결한다(DynamicPropertyRegistry registry) {
        registry.add("ollama.url", () -> "http://127.0.0.1:" + OLLAMA.getAddress().getPort());
    }

    @BeforeEach
    void 모델과_기록을_준비한다() {
        reset(runtime, primary);
        FALLBACK_REQUESTS.clear();
        invalidFallback = false;
    }

    @AfterAll
    static void 대역_서버를_정리한다() {
        OLLAMA.stop(0);
    }

    @Test
    @DisplayName("종료 요약만 베드락으로 만들고 실제 모델 기록과 원문을 저장한다")
    void 정상_요약은_베드락만_사용한다() throws Exception {
        respond("end_turn", false);
        var fixture = fixture();

        assertSummary(closeAndRead(fixture));

        assertThat(FALLBACK_REQUESTS).isEmpty();
        verify(primary, never()).generate(any());
        assertThat(modelRecords(fixture)).containsExactly("openai.gpt-oss-120b-1:0:SUCCESS");
        assertThat(jdbc.queryForObject("SELECT summary FROM chat_sessions WHERE session_id=?",
                String.class, fixture.sessionId())).isEqualTo("기존 내부 기억");
    }

    @Test
    @DisplayName("시간 초과 때 같은 마스킹 입력·스키마로 엑사원을 한 번 호출하고 종료 결과를 저장한다")
    void 시간_초과_대체와_저장_재조회를_검증한다() throws Exception {
        when(runtime.converse(any(ConverseRequest.class)))
                .thenThrow(ApiCallTimeoutException.builder().message("합성 시간 초과").build());
        var fixture = fixture();

        var result = closeAndRead(fixture);

        assertSummary(result);
        assertThat(FALLBACK_REQUESTS).hasSize(1);
        var sent = FALLBACK_REQUESTS.getFirst();
        assertThat(sent.path("model").asText()).isEqualTo("exaone3.5:7.8b");
        assertThat(sent.path("format").isObject()).isTrue();
        assertThat(sent.toString()).contains("***").doesNotContain("900101-1000000");
        assertThat(sent.path("options").path("num_predict").asInt()).isEqualTo(768);
        assertThat(sent.path("options").path("num_ctx").asInt()).isEqualTo(8192);
        assertThat(modelRecords(fixture)).containsExactly("openai.gpt-oss-120b-1:0:TIMEOUT",
                "exaone3.5:7.8b:SUCCESS");
        verify(primary, never()).generate(any());
        assertThat(read(fixture).path("summary")).isEqualTo(result.path("summary"));
        assertThat(FALLBACK_REQUESTS).hasSize(1);
    }

    @Test
    @DisplayName("베드락이 모델 처리 시간 초과 응답을 보내도 같은 상담을 한 번 대체한다")
    void 모델_처리_시간_초과도_대체한다() throws Exception {
        when(runtime.converse(any(ConverseRequest.class))).thenThrow(AwsServiceException.builder()
                .statusCode(408).message("합성 모델 처리 시간 초과").build());
        var fixture = fixture();

        assertSummary(closeAndRead(fixture));

        assertThat(FALLBACK_REQUESTS).hasSize(1);
        assertThat(modelRecords(fixture)).containsExactly("openai.gpt-oss-120b-1:0:TIMEOUT",
                "exaone3.5:7.8b:SUCCESS");
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 502, 503, 504})
    @DisplayName("요청 한도·일시 서버 오류는 같은 유심 상담을 엑사원으로 한 번 대체해 저장한다")
    void 일시_장애는_한_번_대체하고_재조회한다(int statusCode) throws Exception {
        when(runtime.converse(any(ConverseRequest.class))).thenThrow(AwsServiceException.builder()
                .statusCode(statusCode).message("합성 일시 장애").build());
        var fixture = fixture();

        var result = closeAndRead(fixture);

        assertSummary(result);
        assertThat(FALLBACK_REQUESTS).hasSize(1);
        assertThat(FALLBACK_REQUESTS.getFirst().toString()).contains("***").doesNotContain("900101-1000000");
        assertThat(modelRecords(fixture)).containsExactly("openai.gpt-oss-120b-1:0:MODEL_ERROR",
                "exaone3.5:7.8b:SUCCESS");
        assertThat(read(fixture).path("summary")).isEqualTo(result.path("summary"));
        assertThat(FALLBACK_REQUESTS).hasSize(1);
        verify(primary, never()).generate(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 422, 501, 505})
    @DisplayName("인증·권한·요청 오류는 같은 상담이어도 엑사원으로 우회하지 않는다")
    void 인증과_권한_오류는_대체하지_않는다(int statusCode) throws Exception {
        when(runtime.converse(any(ConverseRequest.class))).thenThrow(AwsServiceException.builder()
                .statusCode(statusCode).message("합성 인증 또는 요청 오류").build());
        var fixture = fixture();

        assertFailed(closeAndRead(fixture), fixture);

        assertThat(FALLBACK_REQUESTS).isEmpty();
        assertThat(modelRecords(fixture)).containsExactly("openai.gpt-oss-120b-1:0:MODEL_ERROR");
        verify(primary, never()).generate(any());
    }

    @Test
    @DisplayName("모델 HTTP 연결 장애는 SDK에 감싸져도 같은 유심 상담을 한 번 대체해 저장한다")
    void 확인된_연결_장애를_대체한다() throws Exception {
        when(runtime.converse(any(ConverseRequest.class))).thenThrow(SdkClientException.builder()
                .message("합성 모델 접속 실패")
                .cause(new BedrockTemporaryException(LlmErrorCode.CONNECTION_FAILED)).build());
        var fixture = fixture();

        var result = closeAndRead(fixture);

        assertSummary(result);
        assertThat(FALLBACK_REQUESTS).hasSize(1);
        assertThat(modelRecords(fixture)).containsExactly("openai.gpt-oss-120b-1:0:CONNECTION_FAILED",
                "exaone3.5:7.8b:SUCCESS");
        assertThat(read(fixture).path("summary")).isEqualTo(result.path("summary"));
        assertThat(FALLBACK_REQUESTS).hasSize(1);
    }

    @Test
    @DisplayName("인증 정보 조회 오류는 저장 실패로 끝내고 대체 호출을 하지 않는다")
    void 인증_정보_조회_실패는_요약을_저장하지_않는다() throws Exception {
        when(runtime.converse(any(ConverseRequest.class))).thenThrow(SdkClientException.builder()
                .message("합성 인증 정보 조회 실패").build());
        var fixture = fixture();

        assertFailed(closeAndRead(fixture), fixture);

        assertThat(FALLBACK_REQUESTS).isEmpty();
        assertThat(modelRecords(fixture)).containsExactly("openai.gpt-oss-120b-1:0:CONNECTION_FAILED");
    }

    @Test
    @DisplayName("서버 장애로 대체한 출력에도 기존 문의 번호·출처 검사를 적용한다")
    void 서버_장애의_대체_출력도_검사한다() throws Exception {
        when(runtime.converse(any(ConverseRequest.class))).thenThrow(AwsServiceException.builder()
                .statusCode(503).message("합성 서버 장애").build());
        invalidFallback = true;
        var fixture = fixture();

        assertFailed(closeAndRead(fixture), fixture);

        assertThat(FALLBACK_REQUESTS).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"max_tokens", "guardrail_intervened", "content_filtered"})
    @DisplayName("비정상 종료는 유효한 JSON이 있어도 저장하거나 엑사원으로 우회하지 않는다")
    void 비정상_종료를_차단한다(String reason) throws Exception {
        respond(reason, false);
        var fixture = fixture();

        assertFailed(closeAndRead(fixture), fixture);

        assertThat(FALLBACK_REQUESTS).isEmpty();
        verify(primary, never()).generate(any());
    }

    @Test
    @DisplayName("정상 종료 사유여도 잘못된 JSON은 엑사원으로 우회하지 않는다")
    void 출력_형식_검사를_유지한다() throws Exception {
        respond("end_turn", true);
        var fixture = fixture();

        assertFailed(closeAndRead(fixture), fixture);

        assertThat(FALLBACK_REQUESTS).isEmpty();
    }

    @Test
    @DisplayName("대체 응답에도 기존 출처·문의 번호 검사를 적용한다")
    void 대체_응답의_검사를_완화하지_않는다() throws Exception {
        when(runtime.converse(any(ConverseRequest.class)))
                .thenThrow(ApiCallTimeoutException.builder().message("합성 시간 초과").build());
        invalidFallback = true;
        var fixture = fixture();

        assertFailed(closeAndRead(fixture), fixture);

        assertThat(FALLBACK_REQUESTS).hasSize(1);
    }

    private void respond(String reason, boolean invalid) {
        when(runtime.converse(any(ConverseRequest.class))).thenAnswer(invocation -> {
            ConverseRequest request = invocation.getArgument(0);
            String prompt = request.messages().getFirst().content().getFirst().text();
            assertThat(prompt).contains("***").doesNotContain("900101-1000000");
            assertThat(request.modelId()).isEqualTo("openai.gpt-oss-120b-1:0");
            return ConverseResponse.builder().stopReason(reason)
                    .output(ConverseOutput.fromMessage(Message.builder().content(ContentBlock.fromText(
                            invalid ? "잘못된 생성 결과" : select(prompt))).build())).build();
        });
    }

    private static String select(String prompt) throws IOException {
        var body = JSON.readTree(prompt);
        var result = JSON.createObjectNode();
        var topics = result.putObject("topics");
        body.path("requiredTopicMessageIds").forEach(id -> topics.put(id.asText(), "유심 재발급"));
        var selected = result.putArray("selectedSentenceIds");
        body.path("guidanceCandidates").forEach(candidate -> selected.add(candidate.path("sentenceId").asInt()));
        result.putArray("pendingItems");
        return result.toString();
    }

    private Fixture fixture() throws Exception {
        var actor = new MockHttpSession();
        var created = mvc.perform(post("/api/v1/chat/sessions").session(actor)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long session = mapper.readTree(created).path("result").path("sessionId").asLong();
        jdbc.update("UPDATE chat_sessions SET summary='기존 내부 기억' WHERE session_id=?", session);
        long question = message(session, 1, "USER", "QUESTION", "번호 900101-1000000이고 유심 재발급 비용은요?",
                null, null);
        long answer = message(session, 2, "ASSISTANT", "ANSWER", "유심 재발급 비용은 7,700원이며 "
                + "기기 불량 확인 시에만 무료입니다. 명의자 본인이 신분증을 지참하고 매장을 방문해야 합니다. "
                + "신청은 접수되지 않았습니다.", question, "GROUNDED");
        long execution = jdbc.queryForObject("""
                INSERT INTO chat_executions(session_id,input_message_id,output_message_id,status,ended_at)
                VALUES (?,?,?,'COMPLETED',now()) RETURNING execution_id
                """, Long.class, session, question, answer);
        jdbc.update("""
                INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text,status)
                VALUES (?,?,1,'FAQ','유심 재발급 비용','DONE')
                """, session, question);
        return new Fixture(actor, session, execution);
    }

    private long message(long session, int sequence, String role, String type, String text, Long reply, String basis) {
        return jdbc.queryForObject("""
                INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,reply_to_id,
                    answer_basis,status,completed_at) VALUES (?,?,?,?,?,?,?,'COMPLETED',now()) RETURNING message_id
                """, Long.class, session, sequence, role, type, text, reply, basis);
    }

    private JsonNode closeAndRead(Fixture fixture) throws Exception {
        mvc.perform(patch("/api/v1/chat/sessions/{id}/close", fixture.sessionId()).session(fixture.actor()))
                .andExpect(status().isOk());
        service.process(fixture.sessionId());
        return read(fixture);
    }

    private JsonNode read(Fixture fixture) throws Exception {
        String text = mvc.perform(get("/api/v1/chat/sessions/{id}/summary", fixture.sessionId())
                        .session(fixture.actor())).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString();
        return mapper.readTree(text).path("result");
    }

    private void assertSummary(JsonNode result) {
        assertThat(result.path("summaryStatus").asText()).isEqualTo("COMPLETED");
        assertThat(result.path("summary").path("inquiries").toString())
                .contains("7,700원", "기기 불량 확인 시에만 무료", "명의자 본인", "신분증", "매장을 방문",
                        "신청은 접수되지 않았습니다");
        assertThat(result.toString()).doesNotContain("900101-1000000");
        assertThat(result.path("summary").path("remaining")).isEmpty();
    }

    private void assertFailed(JsonNode result, Fixture fixture) {
        assertThat(result.path("summaryStatus").asText()).isEqualTo("FAILED");
        assertThat(result.path("summary").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT result::text FROM chat_session_end_summaries WHERE session_id=?",
                String.class, fixture.sessionId())).isNull();
    }

    private List<String> modelRecords(Fixture fixture) {
        return jdbc.queryForList("SELECT model || ':' || status FROM llm_generations "
                + "WHERE execution_id=? AND task_type='SUMMARY' ORDER BY generation_id", String.class,
                fixture.executionId());
    }

    private static HttpServer createOllama() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/chat", exchange -> {
                try {
                    var request = JSON.readTree(exchange.getRequestBody());
                    FALLBACK_REQUESTS.add(request);
                    String text = invalidFallback ? "{\"topics\":{\"0\":\"다른 문의\"}}"
                            : select(request.path("messages").get(1).path("content").asText());
                    byte[] body = JSON.writeValueAsBytes(Map.of("message",
                            Map.of("role", "assistant", "content", text), "done", true));
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } finally {
                    exchange.close();
                }
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new IllegalStateException("대체 호출의 로컬 대역 서버를 시작하지 못했습니다.", exception);
        }
    }

    private record Fixture(MockHttpSession actor, long sessionId, long executionId) {}
}
