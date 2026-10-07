package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.ChatSummaryService;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
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

// 실제 채팅 API, EXAONE, bge-m3와 복사한 FAQ 벡터를 사용한다. 모델과 검색 대역은 없다.
@EnabledIfEnvironmentVariable(named = "RUN_TELME121_LIVE", matches = "true")
@SpringBootTest(properties = {"llm.provider=ollama", "chat.summary.grounded-output=true",
        "chat.summary.trigger-messages=6", "chat.summary.retained-messages=2"})
@AutoConfigureMockMvc
@Import(MultiturnLiveApiEvaluationTest.CaptureConfiguration.class)
class MultiturnLiveApiEvaluationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired ChatSummaryService summaries;
    private static final Path DIRECTORY = Path.of(".measure", "telme121", "live-api");
    private static final String RUN = Long.toString(System.currentTimeMillis());

    @Test
    void recordsActualSearchesAndAnswersBeforeAndAfterSummary() throws Exception {
        Files.createDirectories(DIRECTORY);
        for (String slot : List.of("USIM-0001", "ROAMING-0001", "NAME_CHANGE-0001", "TERMINATE-0001")) {
            var gold = jdbc.queryForMap("SELECT faq_id,slot_id,question,answer FROM faqs WHERE slot_id=?", slot);
            for (boolean summarized : List.of(false, true)) {
                long user = jdbc.queryForObject("INSERT INTO users(email,name) VALUES (?,'멀티턴 실측') RETURNING user_id",
                        Long.class, "live-121-" + UUID.randomUUID() + "@example.com");
                long session = jdbc.queryForObject("INSERT INTO chat_sessions(user_id,title) VALUES (?,'멀티턴 실측') RETURNING session_id",
                        Long.class, user);
                MockHttpSession identity = new MockHttpSession();
                identity.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, user);
                var row = new LinkedHashMap<String, Object>();
                row.put("slot", slot);
                row.put("summarized", summarized);
                row.put("goldFaq", gold);
                row.put("sessionId", session);
                row.put("startedAt", Instant.now().toString());
                try {
                    long first = send(session, identity, (String) gold.get("question"));
                    row.put("firstExecution", execution(first));
                    row.put("firstHistory", history(session, identity));
                    if (summarized) {
                        for (int turn = 0; turn < 6; turn++) send(session, identity, "감사합니다");
                        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                                "SELECT summary_through_sequence_no FROM chat_sessions WHERE session_id=?",
                                Integer.class, session)).isPositive());
                    }
                    row.put("summaryBefore", jdbc.queryForMap(
                            "SELECT summary,summary_through_sequence_no FROM chat_sessions WHERE session_id=?", session));
                    String followup = switch (slot) {
                        case "NAME_CHANGE-0001" -> "그건 필요한 서류가 뭐야?";
                        case "TERMINATE-0001" -> "그건 어떻게 신청해?";
                        default -> "그건 비용이 얼마야?";
                    };
                    row.put("followup", followup);
                    long second = send(session, identity, followup);
                    row.put("followupExecution", execution(second));
                    row.put("finalHistory", history(session, identity));
                    row.put("status", "RECORDED");
                } catch (Exception failure) {
                    row.put("status", "ERROR");
                    row.put("error", failure.toString());
                } finally {
                    append(DIRECTORY.resolve("cases-" + RUN + ".jsonl"), mapper, row);
                    // 늦은 요약 작업의 실제 실행 정보도 남길 수 있게 평가 DB에서는 원본을 유지한다.
                }
            }
        }
    }

    @Test
    void recordsAmbiguousReferencesAndExplicitTopicChangesThroughActualApi() throws Exception {
        var cases = List.of(
                Map.of("id", "AMBIGUOUS_TWO_TOPICS", "initial", "유심 재발급 비용과 하루 로밍 요금을 알려주세요.",
                        "followup", "그건 얼마인가요?", "expected", "서로 다른 두 대상 중 하나를 임의로 고르지 않음"),
                Map.of("id", "EXPLICIT_TOPIC_CHANGE", "initial", "유심 재발급 비용이 얼마예요?",
                        "followup", "하루 로밍 요금이 얼마예요?", "expected", "기존 유심이 아닌 새 로밍 주제로 검색"),
                Map.of("id", "IMPLICIT_FOLLOWUP", "initial", "유심 재발급 비용이 얼마예요?",
                        "followup", "비용은 얼마인가요?", "expected", "대상이 생략돼도 기존 유심 주제로 검색"));
        for (var fixture : cases) {
            long user = jdbc.queryForObject("INSERT INTO users(email,name) VALUES (?,'문맥 경계 실측') RETURNING user_id",
                    Long.class, UUID.randomUUID() + "@example.com");
            long session = jdbc.queryForObject("INSERT INTO chat_sessions(user_id,title) VALUES (?,'문맥 경계 실측') RETURNING session_id",
                    Long.class, user);
            var identity = new MockHttpSession();
            identity.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, user);
            var row = new LinkedHashMap<String, Object>();
            row.put("fixture", fixture);
            row.put("sessionId", session);
            row.put("startedAt", Instant.now().toString());
            try {
                row.put("firstExecution", execution(send(session, identity, fixture.get("initial"))));
                row.put("followupExecution", execution(send(session, identity, fixture.get("followup"))));
                row.put("finalHistory", history(session, identity));
                row.put("status", "RECORDED");
            } catch (Exception failure) {
                row.put("status", "ERROR");
                row.put("error", failure.toString());
            } finally {
                append(DIRECTORY.resolve("boundaries-" + RUN + ".jsonl"), mapper, row);
            }
        }
    }

    private long send(long session, MockHttpSession identity, String content) throws Exception {
        var response = mvc.perform(post("/api/v1/chat/sessions/" + session + "/messages").session(identity)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("content", content))))
                .andReturn();
        if (response.getResponse().getStatus() != 201) throw new IllegalStateException(response.getResponse().getContentAsString());
        long execution = mapper.readTree(response.getResponse().getContentAsByteArray()).path("result").path("executionId").asLong();
        await().atMost(Duration.ofSeconds(90)).untilAsserted(() -> assertThat(jdbc.queryForObject(
                "SELECT status FROM chat_executions WHERE execution_id=?", String.class, execution)).isNotEqualTo("RUNNING"));
        return execution;
    }

    private Object execution(long id) {
        return jdbc.queryForMap("SELECT execution_id,status,error_code,pipeline_trace FROM chat_executions WHERE execution_id=?", id);
    }

    private Object history(long session, MockHttpSession identity) throws Exception {
        var response = mvc.perform(get("/api/v1/chat/sessions/" + session + "/messages?size=50").session(identity)).andReturn();
        return mapper.readTree(response.getResponse().getContentAsByteArray());
    }

    private static synchronized void append(Path path, ObjectMapper mapper, Object value) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, mapper.writeValueAsString(value) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception failure) {
            throw new IllegalStateException("실측 원시 자료를 저장하지 못했습니다.", failure);
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
                        final ObjectMapper mapper = new ObjectMapper();
                        public String generate(LlmRequest request) {
                            long started = System.nanoTime();
                            var row = new LinkedHashMap<String, Object>();
                            row.put("request", request);
                            try {
                                String response = client.generate(request);
                                row.put("response", response);
                                return response;
                            } catch (RuntimeException failure) {
                                row.put("error", failure.toString());
                                throw failure;
                            } finally {
                                row.put("elapsedMillis", (System.nanoTime() - started) / 1_000_000);
                                append(DIRECTORY.resolve("model-" + RUN + ".jsonl"), mapper, row);
                            }
                        }
                        public void stream(LlmRequest request, LlmStreamHandler handler) {
                            StringBuilder text = new StringBuilder();
                            long started = System.nanoTime();
                            try {
                                client.stream(request, new LlmStreamHandler() {
                                    public void onToken(String token) { text.append(token); handler.onToken(token); }
                                    public void onComplete() { handler.onComplete(); }
                                    public void onError(Throwable error) { handler.onError(error); }
                                    public void onProgress() { handler.onProgress(); }
                                    public void onRetry(int attempt, Throwable error) { handler.onRetry(attempt, error); }
                                });
                            } finally {
                                append(DIRECTORY.resolve("model-" + RUN + ".jsonl"), mapper,
                                        Map.of("request", request, "rawStream", text.toString(),
                                                "elapsedMillis", (System.nanoTime() - started) / 1_000_000));
                            }
                        }
                    };
                }
            };
        }
    }
}
