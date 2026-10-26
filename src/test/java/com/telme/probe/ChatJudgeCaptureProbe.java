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
import com.telme.chat.service.ChatProcessingPort;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.subscription.service.SubscriptionQueryService;
import com.telme.usage.service.SubscriptionUsageService;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.service.RagAnswerGenerator;
import com.telme.subscription.service.SubscriptionEligibilityChecker;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
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
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;

/** 실제 Chat API부터 상담 처리와 FAQ 검색까지 실행하고, Judge 입력을 별도 파일에 고정한다. */
// 생성 서빙은 TELME_CHAT_JUDGE_LLM_PROVIDER로 바꿀 수 있다(ollama 또는 vllm, docs/04-operations/serving-comparison.md T4)
@SpringBootTest(properties = {
        "llm.provider=${TELME_CHAT_JUDGE_LLM_PROVIDER:ollama}",
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
    private static final Duration EXECUTION_TIMEOUT = Duration.ofMinutes(4);
    // 답변에 영향을 주는 설정만 남긴다. 키·계정 설정(kakao, member.oauth2 등)은 넣지 않는다
    private static final List<String> CONFIG_PREFIXES = List.of("llm", "search", "rag", "embedding", "chat.", "telme.routing",
            "telme.consult", "store.search");
    private static final List<String> CONFIG_KEYS = List.of("llm.provider", "llm.model",
            "telme.consult.persistence-enabled", "telme.consult.llm-enabled",
            "telme.consult.chat-integration-enabled", "telme.consult.rag-integration-enabled",
            "telme.routing.request-inventory-enabled");
    // 되묻기에 고객 답변을 보내는 최대 횟수. 같은 조건을 계속 되물으면 멈춘다
    private static final int MAX_CLARIFICATION_REPLIES = 4;
    // 회원 질문용 테스트 회원. 평가 DB에 없을 때만 만든다(5G 스탠다드, 24개월 약정, 8개월 전 가입)
    private static final String TEST_MEMBER_EMAIL = "judge-member@telme.test";
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private PgvectorFaqSearchService faqSearch;
    @MockitoSpyBean(name = "baseLlmClient") private LlmClient baseLlmClient;
    @MockitoSpyBean private RagAnswerGenerator ragAnswerGenerator;
    @Autowired private List<ChatProcessingPort> processingPorts;
    @Autowired private ApplicationContext context;
    @Autowired private Environment environment;
    @Autowired private SubscriptionQueryService subscriptionQueries;
    @Autowired private SubscriptionUsageService usageService;
    @Value("${ollama.url}") private String ollamaUrl;
    @Value("${vllm.url}") private String vllmUrl;
    @Value("${llm.provider}") private String provider;
    @Value("${llm.model}") private String generatorModel;

    private final List<SearchTrace> searchTraces = new ArrayList<>();
    private final List<LlmRequest> llmRequests = new ArrayList<>();
    private final List<AnswerRequest> ragInputs = new ArrayList<>();
    // Bedrock 호출 기록. 클라이언트 빈을 감싼 쪽(BedrockCallRecording)이 채운다
    private static final List<Map<String, Object>> BEDROCK_CALLS = new ArrayList<>();
    // 켜면 Bedrock 호출의 원본 요청·응답(상담 내용)까지 남긴다. 로컬 측정에서만 쓰고 결과 묶음에는 넣지 않는다
    private static final boolean RECORD_RAW = "true".equals(System.getenv("TELME_CHAT_JUDGE_RAW"));

    @Test
    void capturesActualChatAnswers() throws Exception {
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        // 측정마다 새 평가 DB(telme_judge_로 시작)를 쓸 수 있다. 운영·개발 DB에서는 돌지 않게 이름으로 막는다
        if (database == null || !database.startsWith("telme_judge_")) {
            throw new IllegalStateException("평가는 telme_judge_로 시작하는 평가 DB에서만 실행합니다: " + database);
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
        doAnswer(invocation -> {
            AnswerRequest request = invocation.getArgument(0);
            synchronized (ragInputs) {
                ragInputs.add(request);
            }
            return invocation.callRealMethod();
        }).when(ragAnswerGenerator).generate(any(AnswerRequest.class), any(LlmStreamHandler.class));

        String configuredFixture = System.getenv("TELME_CHAT_JUDGE_FIXTURE");
        Path fixturePath = configuredFixture == null || configuredFixture.isBlank()
                ? Path.of("scripts/chat_judge/data/chat_pipeline_quality_v2_2.json") : Path.of(configuredFixture);
        JsonNode fixtures = mapper.readTree(fixturePath.toFile());
        if (!fixtures.isArray() || fixtures.isEmpty()) {
            throw new IllegalArgumentException("평가 시나리오는 비어 있지 않은 배열이어야 합니다.");
        }
        Path output = outputPath();
        Map<String, Object> run = new LinkedHashMap<>();
        run.put("schemaVersion", 1);
        run.put("createdAt", Instant.now().toString());
        run.put("database", database);
        run.put("generatorModel", generatorModel);
        run.put("generatorProvider", provider);
        if ("vllm".equals(provider)) {
            // vLLM은 다이제스트가 없어 원본 모델 이름(root)을 남긴다
            for (JsonNode model : httpGet(vllmUrl, "/v1/models").path("data")) {
                if (generatorModel.equals(model.path("id").asText())) {
                    run.put("generatorModelDigest", model.path("root").asText());
                    break;
                }
            }
            run.put("generatorVllmVersion", httpGet(vllmUrl, "/version").path("version").asText());
        } else {
            for (JsonNode model : httpGet(ollamaUrl, "/api/tags").path("models")) {
                if (generatorModel.equals(model.path("name").asText())) {
                    run.put("generatorModelDigest", model.path("digest").asText());
                    break;
                }
            }
            run.put("generatorOllamaVersion", httpGet(ollamaUrl, "/api/version").path("version").asText());
        }
        if (!run.containsKey("generatorModelDigest")) {
            throw new IllegalStateException(provider + "에서 답변 생성 모델을 찾지 못했습니다: " + generatorModel);
        }
        run.put("gitHead", gitHead());
        run.put("mainSourceSha256", mainSourceHash());
        run.put("fixtureSha256", sha256(Files.readAllBytes(fixturePath)));
        run.put("fixturePath", fixturePath.toString());
        run.put("captureKind", "SPRING_CHAT_API_ACTUAL_ANSWERS");
        run.put("plannedCaseCount", fixtures.size());
        int plannedTurns = 0;
        for (JsonNode fixture : fixtures) {
            plannedTurns += fixture.path("turns").size();
        }
        run.put("plannedTurnCount", plannedTurns);
        run.put("captureComplete", false);
        run.put("processingPorts", processingPorts.stream()
                .map(port -> AopUtils.getTargetClass(port).getName()).toList());
        run.put("faqCount", jdbc.queryForObject("SELECT count(*) FROM faqs", Long.class));
        run.put("embeddingCount", jdbc.queryForObject("SELECT count(*) FROM faq_embeddings", Long.class));
        run.put("faqSnapshotHash", jdbc.queryForObject(
                "SELECT md5(string_agg(faq_id::text || ':' || question || ':' || answer, '' ORDER BY faq_id)) "
                        + "FROM faqs", String.class));
        run.put("appConfig", appConfig());
        run.put("serviceData", serviceData());
        // 지명으로 찾는 매장 검색은 카카오 로컬 API가 필요하다. 키 값은 남기지 않고 설정 여부만 남긴다
        run.put("kakaoConfigured", !environment.getProperty("kakao.local.rest-api-key", "").startsWith("dummy"));
        run.put("cases", new ArrayList<>());
        write(output, run);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cases = (List<Map<String, Object>>) run.get("cases");
        for (JsonNode fixture : fixtures) {
            MockHttpSession session = new MockHttpSession();
            Map<String, Object> caseResult = new LinkedHashMap<>();
            String actor = fixture.path("actor").asText("GUEST");
            if ("MEMBER".equals(actor)) {
                long userId = ensureTestMember();
                session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
                caseResult.put("memberData", memberData(userId));
            }
            long sessionId = createSession(session, fixture.path("caseId").asText());
            List<Map<String, Object>> turns = new ArrayList<>();
            caseResult.put("caseId", fixture.path("caseId").asText());
            caseResult.put("actor", actor);
            caseResult.put("category", fixture.path("category").asText());
            caseResult.put("suite", fixture.path("suite").asText("flow_regression"));
            caseResult.put("questionType", fixture.path("questionType").asText());
            caseResult.put("sessionId", sessionId);
            caseResult.put("turns", turns);
            cases.add(caseResult);

            for (JsonNode turn : fixture.path("turns")) {
                Map<String, Object> result = captureConversationTurn(session, sessionId, turn);
                turns.add(result);
                write(output, run);
                System.out.printf("[Chat Judge] %s %s -> %s, %d searches%n",
                        fixture.path("caseId").asText(), turn.path("question").asText(),
                        result.get("executionStatus"), ((List<?>) result.get("searches")).size());
            }
        }
        run.put("captureComplete", true);
        run.put("completedAt", Instant.now().toString());
        write(output, run);
        System.out.println("[Chat Judge] 생성 결과: " + output.toAbsolutePath());
    }

    // 질문을 보내고, 앱이 되물으면 평가셋에 적힌 고객 답변을 보내 최종 응답까지 받는다.
    // 마지막 응답이 판정 대상이고, 앞선 되묻기 단계는 clarificationExchanges에 그대로 남긴다
    private Map<String, Object> captureConversationTurn(MockHttpSession session, long sessionId, JsonNode fixture)
            throws Exception {
        Map<String, Object> result = captureTurn(session, sessionId, fixture, fixture.path("question").asText(), null);
        List<JsonNode> replies = new ArrayList<>();
        fixture.path("clarificationReplies").forEach(replies::add);
        List<Map<String, Object>> exchanges = new ArrayList<>();
        // 프론트(heldCoords.ts)처럼 '현재 위치'로 답한 좌표를 되묻기가 이어지는 동안 다음 답에도 붙인다.
        // 서버는 좌표를 저장하지 않아, 붙이지 않으면 업무를 답한 뒤 지역을 다시 묻는다
        JsonNode heldCoordinates = null;
        while (exchanges.size() < MAX_CLARIFICATION_REPLIES && isClarification(result)) {
            List<String> askedKeys = askedKeys(result);
            JsonNode reply = pickReply(replies, askedKeys);
            if (reply == null) {
                result.put("unansweredClarificationKeys", askedKeys);
                break;
            }
            // 조건을 정한 답변은 앱이 같은 조건을 다시 물으면 한 번 더 쓴다(고객이 같은 답을 다시 하는 것). 재질문은 Judge가 따로 잡는다
            if (reply.path("whenKey").isMissingNode() || reply.path("whenKey").isNull()) {
                replies.remove(reply);
            }
            Map<String, Object> exchange = new LinkedHashMap<>(result);
            exchange.remove("fixture");
            exchange.put("askedKeys", askedKeys);
            exchange.put("customerReply", reply);
            exchanges.add(exchange);
            JsonNode coordinates = reply.path("coordinates");
            if (coordinates.isObject()) {
                heldCoordinates = coordinates;
            }
            result = captureTurn(session, sessionId, fixture, reply.path("content").asText(), heldCoordinates);
        }
        result.put("clarificationExchanges", exchanges);
        return result;
    }

    private static boolean isClarification(Map<String, Object> result) {
        return result.get("outputMessage") instanceof JsonNode output
                && "CLARIFICATION".equals(output.path("messageType").asText());
    }

    // 되묻기 메시지가 기다리는 조건 이름(매장은 serviceType, location. FAQ 조건 이름은 질문마다 정해진다)
    private List<String> askedKeys(Map<String, Object> result) {
        JsonNode output = (JsonNode) result.get("outputMessage");
        return jdbc.queryForList("SELECT condition_key FROM consult_conditions WHERE asked_message_id = ? "
                + "AND status = 'PENDING' ORDER BY condition_key", String.class, output.path("messageId").asLong());
    }

    // 묻는 조건에 맞춘 답변(whenKey)을 먼저, 없으면 조건을 정하지 않은 답변을 순서대로 쓴다.
    // 되묻기 횟수는 MAX_CLARIFICATION_REPLIES로 막는다
    private static JsonNode pickReply(List<JsonNode> replies, List<String> askedKeys) {
        for (JsonNode reply : replies) {
            if (askedKeys.contains(reply.path("whenKey").asText(""))) return reply;
        }
        for (JsonNode reply : replies) {
            if (reply.path("whenKey").isMissingNode() || reply.path("whenKey").isNull()) return reply;
        }
        return null;
    }

    private Map<String, Object> captureTurn(MockHttpSession session, long sessionId, JsonNode fixture, String content,
            JsonNode coordinates) throws Exception {
        synchronized (searchTraces) {
            searchTraces.clear();
        }
        synchronized (llmRequests) {
            llmRequests.clear();
        }
        synchronized (ragInputs) {
            ragInputs.clear();
        }
        synchronized (BEDROCK_CALLS) {
            BEDROCK_CALLS.clear();
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("content", content);
        if (coordinates != null) {
            request.put("latitude", coordinates.path("latitude").asDouble());
            request.put("longitude", coordinates.path("longitude").asDouble());
        }
        long started = System.nanoTime();
        JsonNode sent = body(mvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(request)))
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
        result.put("sentContent", content);
        result.put("sentCoordinates", coordinates);
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
        synchronized (ragInputs) {
            result.put("ragInputs", List.copyOf(ragInputs));
        }
        result.put("generatorCallRecords", generations);
        synchronized (BEDROCK_CALLS) {
            result.put("bedrockCalls", List.copyOf(BEDROCK_CALLS));
        }
        result.put("outputMessage", outputMessage);
        result.put("savedSources", savedSources);
        // 요금제 답변(planAnswer)처럼 FAQ 밖 데이터로 답한 경로를 Judge가 알 수 있게 실행 기록을 남긴다
        String trace = jdbc.queryForObject(
                "SELECT pipeline_trace::text FROM chat_executions WHERE execution_id = ?", String.class, executionId);
        result.put("pipelineTrace", trace == null ? null : mapper.readTree(trace));
        if (outputMessage != null && outputMessage.path("storeResults").isArray()
                && !outputMessage.path("storeResults").isEmpty()) {
            result.put("storeDetails", storeDetails(outputMessage.path("storeResults")));
        }
        return result;
    }

    // 매장 결과에 없는 취급 업무와 영업시간. 업무 조건에 맞는 매장을 보여 줬는지 판정할 때 쓴다
    private List<Map<String, Object>> storeDetails(JsonNode stores) {
        List<Map<String, Object>> details = new ArrayList<>();
        for (JsonNode store : stores) {
            long storeId = store.path("storeId").asLong();
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("storeId", storeId);
            detail.put("services", jdbc.queryForList("SELECT t.code FROM store_services s JOIN store_service_types t "
                    + "ON t.service_type_id = s.service_type_id WHERE s.store_id = ? ORDER BY t.code", String.class, storeId));
            detail.put("hours", jdbc.queryForList("SELECT day_of_week, open_time::text AS open_time, "
                    + "close_time::text AS close_time, is_closed AS closed FROM store_hours WHERE store_id = ? ORDER BY day_of_week",
                    storeId));
            details.add(detail);
        }
        return details;
    }

    private long ensureTestMember() {
        List<Long> found = jdbc.queryForList("SELECT user_id FROM users WHERE email = ?", Long.class, TEST_MEMBER_EMAIL);
        long userId = found.isEmpty()
                ? jdbc.queryForObject("INSERT INTO users (email, name, role, status, birth_date) "
                        + "VALUES (?, '평가 회원', 'USER', 'ACTIVE', DATE '1990-05-01') RETURNING user_id",
                        Long.class, TEST_MEMBER_EMAIL)
                : found.getFirst();
        Integer active = jdbc.queryForObject(
                "SELECT count(*) FROM user_subscriptions WHERE user_id = ? AND status = 'ACTIVE'", Integer.class, userId);
        if (active == null || active == 0) {
            Long subscriptionId = jdbc.queryForObject("INSERT INTO user_subscriptions (user_id, status, subscribed_at, "
                    + "created_at, updated_at, contract_months, contract_discount_percent) VALUES (?, 'ACTIVE', "
                    + "date_trunc('day', now() - interval '8 months') + interval '9 hours', now(), now(), 24, 25) "
                    + "RETURNING subscription_id", Long.class, userId);
            jdbc.update("INSERT INTO subscription_histories (subscription_id, type, to_plan_id, effective_from, created_at) "
                    + "SELECT s.subscription_id, 'SUBSCRIBE', p.plan_id, s.subscribed_at, now() "
                    + "FROM user_subscriptions s, plans p WHERE s.subscription_id = ? AND p.code = 'FIVE_G_STANDARD'",
                    subscriptionId);
        }
        return userId;
    }

    // 회원 질문 답변의 근거. 앱이 회원 답변을 만들 때 부르는 서비스를 그대로 불러 대화 직전 값을 남긴다
    private Map<String, Object> memberData(long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        data.put("subscription", call(() -> subscriptionQueries.getMine(userId)));
        data.put("terminationPenalty", call(() -> subscriptionQueries.getTerminationPenalty(userId)));
        data.put("usage", call(() -> usageService.getOverview(userId)));
        // 사용량 기반 추천이 쓰는 지난 달 평균 사용량
        data.put("usageDemand", call(() -> usageService.getDemand(userId).orElse(null)));
        return data;
    }

    private Object call(java.util.function.Supplier<Object> supplier) {
        try {
            return mapper.valueToTree(supplier.get());
        } catch (RuntimeException failure) {
            return Map.of("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    // 요금제 답변의 근거가 되는 판매 요금제 표와 가입 연령 기준
    private Map<String, Object> serviceData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("plans", jdbc.queryForList(
                "SELECT code, name, network_type, target_group, monthly_fee, data_gb, is_unlimited, voice_minutes, "
                        + "is_voice_unlimited, sms_count, is_sms_unlimited, status, sort_order "
                        + "FROM plans ORDER BY sort_order, plan_id"));
        data.put("planEligibility", Map.of(
                "youthMinAge", SubscriptionEligibilityChecker.YOUTH_MIN_AGE,
                "youthMaxAge", SubscriptionEligibilityChecker.YOUTH_MAX_AGE,
                "seniorMinAge", SubscriptionEligibilityChecker.SENIOR_MIN_AGE));
        return data;
    }

    // 실제 적용된 설정 값. 기본값이 코드(@DefaultValue)에 있는 설정도 남도록 설정 객체를 그대로 기록한다
    private Map<String, Object> appConfig() {
        Map<String, Object> config = new TreeMap<>();
        for (Object bean : context.getBeansWithAnnotation(ConfigurationProperties.class).values()) {
            Class<?> type = AopUtils.getTargetClass(bean);
            ConfigurationProperties annotation = AnnotationUtils.findAnnotation(type, ConfigurationProperties.class);
            String prefix = annotation == null ? "" : annotation.prefix().isBlank() ? annotation.value() : annotation.prefix();
            if (CONFIG_PREFIXES.stream().anyMatch(prefix::startsWith)) {
                try {
                    config.put(prefix, withoutSecrets(mapper.valueToTree(bean)));
                } catch (IllegalArgumentException unserializable) {
                    config.put(prefix, type.getName());
                }
            }
        }
        for (String key : CONFIG_KEYS) {
            config.put(key, environment.getProperty(key));
        }
        return config;
    }

    private static JsonNode withoutSecrets(JsonNode node) {
        if (node instanceof com.fasterxml.jackson.databind.node.ObjectNode object) {
            List<String> secrets = new ArrayList<>();
            object.fieldNames().forEachRemaining(name -> {
                if (name.toLowerCase().matches(".*(key|secret|token|password).*")) secrets.add(name);
            });
            object.remove(secrets);
            object.forEach(ChatJudgeCaptureProbe::withoutSecrets);
        }
        return node;
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

    private JsonNode httpGet(String baseUrl, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
        HttpResponse<byte[]> response = HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("모델 서버 정보 조회 실패: " + baseUrl + path + " " + response.statusCode());
        }
        return body(response.body());
    }

    private Path outputPath() throws IOException {
        String configured = System.getenv("TELME_CHAT_JUDGE_OUT");
        Path path = configured == null || configured.isBlank()
                ? Path.of(".measure", "chat-judge-capture-" + FILE_TIME.format(Instant.now()) + ".json")
                : Path.of(configured);
        Files.createDirectories(path.toAbsolutePath().getParent());
        if (Files.exists(path)) {
            throw new IOException("기존 생성 결과를 덮어쓰지 않습니다: " + path);
        }
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

    // Bedrock 클라이언트를 감싸 호출마다 토큰·지연을 남긴다. Bedrock을 끈 실행(llm.bedrock.enabled=false)에는 빈이 없어 그대로다.
    // 앱 코드는 상담 내용이라 모델 입출력을 남기지 않으므로 측정에서만 여기서 남긴다
    @TestConfiguration
    static class BedrockCallRecording {
        @Bean
        static BeanPostProcessor bedrockCallRecorder() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (!(bean instanceof BedrockRuntimeClient client)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(BedrockRuntimeClient.class.getClassLoader(),
                            new Class<?>[] {BedrockRuntimeClient.class}, (proxy, method, args) -> {
                                if ("converse".equals(method.getName()) && args != null && args.length == 1
                                        && args[0] instanceof ConverseRequest request) {
                                    return recordConverse(client, request);
                                }
                                try {
                                    return method.invoke(client, args);
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                            });
                }
            };
        }

        private static ConverseResponse recordConverse(BedrockRuntimeClient client, ConverseRequest request) {
            long started = System.nanoTime();
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("modelId", request.modelId());
            // 시스템 프롬프트 첫 줄로 어느 호출인지 알 수 있다. 고정 문구라 상담 내용이 없다
            String system = request.system().stream().map(SystemContentBlock::text).filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"));
            call.put("systemHead", system.lines().findFirst().orElse(""));
            try {
                ConverseResponse response = client.converse(request);
                call.put("elapsedMs", Duration.ofNanos(System.nanoTime() - started).toMillis());
                if (response.usage() != null) {
                    call.put("inputTokens", response.usage().inputTokens());
                    call.put("outputTokens", response.usage().outputTokens());
                }
                call.put("latencyMs", response.metrics() == null ? null : response.metrics().latencyMs());
                call.put("stopReason", response.stopReasonAsString());
                if (RECORD_RAW) {
                    call.put("system", system);
                    call.put("messages", request.messages().stream().map(message -> Map.of(
                            "role", message.roleAsString(),
                            "text", message.content().stream().map(ContentBlock::text).filter(Objects::nonNull)
                                    .collect(Collectors.joining("\n")))).toList());
                    call.put("response", response.output() == null || response.output().message() == null ? null
                            : response.output().message().content().stream().map(ContentBlock::text)
                                    .filter(Objects::nonNull).collect(Collectors.joining()));
                }
                return response;
            } catch (RuntimeException e) {
                call.put("elapsedMs", Duration.ofNanos(System.nanoTime() - started).toMillis());
                call.put("error", e.getClass().getSimpleName());
                throw e;
            } finally {
                synchronized (BEDROCK_CALLS) {
                    BEDROCK_CALLS.add(call);
                }
            }
        }
    }

    private record SearchTrace(String query, List<FaqSearchResponse> results) {
    }
}
