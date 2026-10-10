package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

// 이전 고객 발언만 고정하고 현재 질문은 실제 채팅 API, 검색과 EXAONE으로 처리한다.
@EnabledIfEnvironmentVariable(named = "RUN_TELME139_LIVE", matches = "true")
@SpringBootTest(properties = {"llm.provider=ollama", "chat.summary.trigger-messages=1000",
        "chat.summary.trigger-tokens=100000"})
@AutoConfigureMockMvc
@Import(ContextResolutionLiveEvaluationTest.CaptureConfiguration.class)
class ContextResolutionLiveEvaluationTest {
    private static final Path DIRECTORY = Path.of(".measure", "telme139",
            System.getenv().getOrDefault("TELME139_RUN", "improved"));
    private static volatile String activeCase;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @Test
    void recordsFixedCasesThroughActualChatApi() throws Exception {
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        assertThat(database).startsWith("telme_139_");
        if ("true".equals(System.getenv("TELME139_INIT_ONLY"))) return;
        assertThat(jdbc.queryForObject("SELECT count(*) FROM faq_embeddings", Integer.class)).isGreaterThan(1000);
        var fixtures = mapper.readTree(Files.readString(Path.of("scripts/context-resolution-evaluation/cases.json")));
        List<String> errors = new ArrayList<>();
        String fixtureKey = System.getenv().getOrDefault("TELME139_FIXTURE_KEY", "cases");
        assertThat(fixtures.path(fixtureKey).isArray()).isTrue();
        for (JsonNode fixture : fixtures.path(fixtureKey)) {
            String split = System.getenv("TELME139_SPLIT");
            if (split != null && !List.of(split.split(",")).contains(fixture.path("split").asText())) continue;
            activeCase = fixture.path("id").asText();
            var row = new LinkedHashMap<String, Object>();
            row.put("fixture", fixture);
            long started = System.nanoTime();
            try {
                long user = jdbc.queryForObject("INSERT INTO users(email,name) VALUES (?,'문맥 판정 평가') RETURNING user_id",
                        Long.class, UUID.randomUUID() + "@example.com");
                long session = jdbc.queryForObject("INSERT INTO chat_sessions(user_id,title) VALUES (?,'문맥 판정 평가') RETURNING session_id",
                        Long.class, user);
                var identity = new MockHttpSession();
                identity.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, user);
                List<Long> sources = new ArrayList<>();
                int sequence = 0;
                for (JsonNode history : fixture.path("history")) {
                    sources.add(jdbc.queryForObject("INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,status)"
                            + " VALUES (?,?,'USER','QUESTION',?,'COMPLETED') RETURNING message_id",
                            Long.class, session, ++sequence, history.asText()));
                }
                row.put("sourceMessageIds", sources);
                row.put("sessionId", session);
                var response = mvc.perform(post("/api/v1/chat/sessions/" + session + "/messages").session(identity)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("content", fixture.path("question").asText()))))
                        .andReturn().getResponse();
                assertThat(response.getStatus()).isEqualTo(201);
                long execution = mapper.readTree(response.getContentAsByteArray()).path("result").path("executionId").asLong();
                row.put("executionId", execution);
                await().atMost(Duration.ofSeconds(180)).pollInterval(Duration.ofMillis(250)).until(() ->
                        !"RUNNING".equals(jdbc.queryForObject("SELECT status FROM chat_executions WHERE execution_id=?", String.class, execution)));
                var stored = jdbc.queryForMap("SELECT status,error_code,pipeline_trace::text AS trace,output_message_id"
                        + " FROM chat_executions WHERE execution_id=?", execution);
                row.put("execution", stored);
                row.put("trace", mapper.readTree((String) stored.get("trace")));
                row.put("messages", jdbc.queryForList("SELECT message_id,sequence_no,role,message_type,content,status,answer_basis"
                        + " FROM chat_messages WHERE session_id=? ORDER BY sequence_no", session));
                row.put("consultRequests", jdbc.queryForList("SELECT consult_request_id,query_text,status FROM consult_requests WHERE session_id=? ORDER BY consult_request_id", session));
                row.put("sources", jdbc.queryForList("SELECT s.* FROM message_sources s JOIN chat_messages m ON m.message_id=s.message_id WHERE m.session_id=?", session));
                row.put("generations", jdbc.queryForList("SELECT task_type,model,prompt_version,status,total_ms FROM llm_generations WHERE execution_id=? ORDER BY generation_id", execution));
                row.put("recorded", true);
            } catch (Exception | AssertionError error) {
                row.put("error", error.toString());
                errors.add(fixture.path("id").asText() + ": " + error);
            } finally {
                row.put("elapsedMillis", (System.nanoTime() - started) / 1_000_000);
                append(DIRECTORY.resolve("cases.jsonl"), row);
                System.out.println("TELME139 " + fixture.path("id").asText() + " recorded");
            }
        }
        assertThat(errors).isEmpty();
    }

    private static synchronized void append(Path file, Object row) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new ObjectMapper().writeValueAsString(row) + "\n",
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception error) {
            throw new IllegalStateException("평가 원시 자료를 저장하지 못했습니다.", error);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class CaptureConfiguration {
        @Bean
        static BeanPostProcessor captureRequests() {
            return new BeanPostProcessor() {
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!name.equals("baseLlmClient") || !(bean instanceof LlmClient client)) return bean;
                    return new LlmClient() {
                        public String generate(LlmRequest request) {
                            long start = System.nanoTime();
                            var row = new LinkedHashMap<String, Object>();
                            row.put("request", request);
                            row.put("caseId", activeCase);
                            try {
                                String output = client.generate(request);
                                row.put("response", output);
                                return output;
                            } catch (RuntimeException error) {
                                row.put("error", error.toString());
                                throw error;
                            } finally {
                                row.put("elapsedMillis", (System.nanoTime() - start) / 1_000_000);
                                append(DIRECTORY.resolve("model.jsonl"), row);
                            }
                        }
                        public void stream(LlmRequest request, LlmStreamHandler handler) {
                            long start = System.nanoTime();
                            StringBuilder output = new StringBuilder();
                            var row = new LinkedHashMap<String, Object>();
                            row.put("request", request);
                            row.put("caseId", activeCase);
                            try {
                                client.stream(request, new LlmStreamHandler() {
                                    public void onToken(String token) { output.append(token); handler.onToken(token); }
                                    public void onComplete() { handler.onComplete(); }
                                    public void onProgress() { handler.onProgress(); }
                                    public void onRetry(int attempt, Throwable error) { handler.onRetry(attempt, error); }
                                    public void onError(Throwable error) { row.put("error", error.toString()); handler.onError(error); }
                                });
                            } finally {
                                row.put("response", output.toString());
                                row.put("elapsedMillis", (System.nanoTime() - start) / 1_000_000);
                                append(DIRECTORY.resolve("model.jsonl"), row);
                            }
                        }
                    };
                }
            };
        }
    }
}
