package com.telme.probe;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.PgvectorFaqSearchService;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/** 실제 Chat API부터 상담 처리와 FAQ 검색까지 실행하고, Judge 입력을 별도 파일에 고정한다. */
@SpringBootTest(properties = {
        "llm.provider=ollama",
        "telme.consult.persistence-enabled=true",
        "telme.consult.llm-enabled=true",
        "telme.consult.chat-integration-enabled=true",
        "telme.consult.rag-integration-enabled=true",
        "telme.chat.pipeline.enabled=false",
        "chat.summary.trigger-messages=1000",
        "chat.summary.trigger-tokens=100000"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "TELME_CHAT_JUDGE_PROBE", matches = "true")
class ChatJudgeCaptureProbe {
    private static final Path FIXTURE = Path.of("scripts/data/chat_judge_pilot.json");
    private static final Duration EXECUTION_TIMEOUT = Duration.ofMinutes(4);
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private PgvectorFaqSearchService faqSearch;
    @MockitoSpyBean(name = "baseLlmClient") private LlmClient baseLlmClient;
    @Value("${ollama.url}") private String ollamaUrl;
    @Value("${llm.model}") private String generatorModel;

    private final List<SearchTrace> searchTraces = new ArrayList<>();
    private final List<LlmRequest> llmRequests = new ArrayList<>();

    @Test
    void capturesActualChatAnswers() throws Exception {
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        if (!"telme_judge_eval".equals(database)) {
            throw new IllegalStateException("평가는 telme_judge_eval DB에서만 실행합니다: " + database);
        }
        doAnswer(invocation -> {
            FaqSearchRequest request = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            List<FaqSearchResponse> results = (List<FaqSearchResponse>) invocation.callRealMethod();
            synchronized (searchTraces) {
                searchTraces.add(new SearchTrace(request.query(), List.copyOf(results)));
            }
            return results;
        }).when(faqSearch).search(any(FaqSearchRequest.class));
        doAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            synchronized (llmRequests) {
                llmRequests.add(request);
            }
            return invocation.callRealMethod();
        }).when(baseLlmClient).generate(any(LlmRequest.class));
        doAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            synchronized (llmRequests) {
                llmRequests.add(request);
            }
            return invocation.callRealMethod();
        }).when(baseLlmClient).stream(any(LlmRequest.class), any(LlmStreamHandler.class));

        JsonNode fixtures = mapper.readTree(FIXTURE.toFile());
        if (!fixtures.isArray() || fixtures.size() != 8) {
            throw new IllegalArgumentException("평가 시나리오는 정확히 8개여야 합니다.");
        }
        Path output = outputPath();
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("schemaVersion", 1);
        run.put("createdAt", Instant.now().toString());
        run.put("database", database);
        run.put("generatorModel", generatorModel);
        JsonNode tags = ollamaGet("/api/tags");
        for (JsonNode model : tags.path("models")) {
            if (generatorModel.equals(model.path("name").asText())) {
                run.put("generatorModelDigest", model.path("digest").asText());
                break;
            }
        }
        if (!run.containsKey("generatorModelDigest")) {
            throw new IllegalStateException("Ollama에서 답변 생성 모델을 찾지 못했습니다: " + generatorModel);
        }
        run.put("generatorOllamaVersion", ollamaGet("/api/version").path("version").asText());
        run.put("gitHead", gitHead());
        run.put("mainSourceSha256", mainSourceHash());
        run.put("fixtureSha256", sha256(Files.readAllBytes(FIXTURE)));
        run.put("cases", new ArrayList<>());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cases = (List<Map<String, Object>>) run.get("cases");
        for (JsonNode fixture : fixtures) {
            MockHttpSession session = new MockHttpSession();
            long sessionId = createSession(session, fixture.path("caseId").asText());
            List<Map<String, Object>> turns = new ArrayList<>();
            Map<String, Object> caseResult = new LinkedHashMap<>();
            caseResult.put("caseId", fixture.path("caseId").asText());
            caseResult.put("category", fixture.path("category").asText());
            caseResult.put("sessionId", sessionId);
            caseResult.put("turns", turns);
            cases.add(caseResult);

            for (JsonNode turn : fixture.path("turns")) {
                synchronized (searchTraces) {
                    searchTraces.clear();
                }
                synchronized (llmRequests) {
                    llmRequests.clear();
                }
                Map<String, Object> result = captureTurn(session, sessionId, turn);
                turns.add(result);
                write(output, run);
                System.out.printf("[Chat Judge] %s %s -> %s, %d searches%n",
                        fixture.path("caseId").asText(), turn.path("question").asText(),
                        result.get("executionStatus"), ((List<?>) result.get("searches")).size());
            }
        }
        System.out.println("[Chat Judge] 생성 결과: " + output.toAbsolutePath());
    }

    private Map<String, Object> captureTurn(MockHttpSession session, long sessionId, JsonNode fixture)
            throws Exception {
        String question = fixture.path("question").asText();
        long started = System.nanoTime();
        JsonNode sent = body(mvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("content", question))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
        long inputMessageId = sent.path("result").path("messageId").asLong();
        long executionId = sent.path("result").path("executionId").asLong();
        Map<String, Object> execution = waitForExecution(executionId);
        long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();

        JsonNode history = body(mvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(session).param("size", "50"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        Long outputMessageId = (Long) execution.get("output_message_id");
        JsonNode outputMessage = null;
        for (JsonNode message : history.path("result").path("messages")) {
            if (outputMessageId != null && message.path("messageId").asLong() == outputMessageId) {
                outputMessage = message;
                break;
            }
        }
        List<Map<String, Object>> routes = jdbc.queryForList(
                "SELECT intent, refined_query, extracted_conditions::text AS extracted_conditions, "
                        + "confidence, method FROM query_routings WHERE message_id = ?", inputMessageId);
        List<Map<String, Object>> consultRequests = jdbc.queryForList(
                "SELECT r.consult_request_id, r.subquery_order, r.intent, r.query_text, r.status, "
                        + "c.condition_key, c.condition_value, c.status AS condition_status "
                        + "FROM consult_requests r LEFT JOIN consult_conditions c "
                        + "ON c.consult_request_id = r.consult_request_id "
                        + "WHERE r.origin_message_id = ? ORDER BY r.subquery_order, c.condition_key",
                inputMessageId);
        List<Map<String, Object>> confirmedConditions = consultRequests.stream()
                .filter(row -> "FILLED".equals(row.get("condition_status")))
                .map(row -> Map.<String, Object>of(
                        "consultRequestId", row.get("consult_request_id"),
                        "key", row.get("condition_key"),
                        "value", row.get("condition_value")))
                .toList();
        List<SearchTrace> snapshots;
        synchronized (searchTraces) {
            snapshots = List.copyOf(searchTraces);
        }
        List<LlmRequest> promptSnapshots;
        synchronized (llmRequests) {
            promptSnapshots = List.copyOf(llmRequests);
        }
        List<Map<String, Object>> generations = jdbc.queryForList(
                "SELECT task_type, attempt, model, prompt_version, context_count, first_token_ms, "
                        + "total_ms, status, error_message FROM llm_generations WHERE execution_id = ? "
                        + "ORDER BY generation_id",
                executionId);
        Object savedSources = List.of();
        if (outputMessageId != null) {
            JsonNode response = body(mvc.perform(get("/api/v1/chat/messages/{messageId}/sources", outputMessageId)
                            .session(session))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
            savedSources = response.path("result").path("sources");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fixture", fixture);
        result.put("inputMessageId", inputMessageId);
        result.put("executionId", executionId);
        result.put("executionStatus", execution.get("status"));
        result.put("errorCode", execution.get("error_code"));
        result.put("durationMs", durationMs);
        result.put("route", routes.isEmpty() ? null : routes.getFirst());
        result.put("consultRequests", consultRequests);
        result.put("confirmedConditions", confirmedConditions);
        result.put("searches", snapshots);
        result.put("generatorRequests", promptSnapshots);
        result.put("generatorCallRecords", generations);
        result.put("outputMessage", outputMessage);
        result.put("savedSources", savedSources);
        return result;
    }

    private long createSession(MockHttpSession session, String caseId) throws Exception {
        JsonNode created = body(mvc.perform(post("/api/v1/chat/sessions")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("title", "Judge 평가 " + caseId))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
        return created.path("result").path("sessionId").asLong();
    }

    private Map<String, Object> waitForExecution(long executionId) throws InterruptedException {
        long deadline = System.nanoTime() + EXECUTION_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT status, error_code, output_message_id FROM chat_executions WHERE execution_id = ?",
                    executionId);
            if (!"RUNNING".equals(row.get("status"))) {
                return row;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("채팅 실행이 끝나지 않았습니다: executionId=" + executionId);
    }

    private JsonNode body(byte[] bytes) throws IOException {
        return mapper.readTree(bytes);
    }

    private JsonNode ollamaGet(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ollamaUrl + path)).GET().build();
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Ollama 정보 조회 실패: " + response.statusCode());
        }
        return body(response.body());
    }

    private Path outputPath() throws IOException {
        String configured = System.getenv("TELME_CHAT_JUDGE_OUT");
        Path path = configured == null || configured.isBlank()
                ? Path.of(".measure", "chat-judge-capture-" + FILE_TIME.format(Instant.now()) + ".json")
                : Path.of(configured);
        Files.createDirectories(path.toAbsolutePath().getParent());
        return path;
    }

    private void write(Path path, Map<String, Object> data) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), data);
    }

    private String gitHead() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "rev-parse", "HEAD").start();
        String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) {
            throw new IOException("git HEAD를 읽지 못했습니다.");
        }
        return value;
    }

    private String mainSourceHash() throws IOException {
        MessageDigest digest = digest();
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path path : files.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList()) {
                digest.update(path.toString().getBytes(StandardCharsets.UTF_8));
                digest.update(Files.readAllBytes(path));
            }
        }
        digest.update(Files.readAllBytes(Path.of("src/main/resources/application.yml")));
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private String sha256(byte[] bytes) {
        return java.util.HexFormat.of().formatHex(digest().digest(bytes));
    }

    private MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record SearchTrace(String query, List<FaqSearchResponse> results) {
    }
}
