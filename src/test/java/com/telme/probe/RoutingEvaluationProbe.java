package com.telme.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.service.ChatContext;
import com.telme.chat.service.ChatContextMessage;
import com.telme.consult.repository.ConsultRequestRepository;
import com.telme.intent.converter.IntentConverter;
import com.telme.intent.dto.res.LlmRequestInventoryPayload;
import com.telme.intent.dto.res.LlmRoutingPayload;
import com.telme.intent.entity.QueryRouting;
import com.telme.consult.entity.ConsultRequest;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.repository.QueryRoutingRepository;
import com.telme.intent.service.QueryRoutingService;
import com.telme.intent.service.RuleBasedRoutingFallback;
import com.telme.intent.service.RoutingPromptTemplates;
import com.telme.intent.service.RoutingQuestionNormalizer;
import com.telme.intent.service.UnsupportedCompoundQuestionException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 실제 Ollama 출력으로 현재 단일 상담 라우팅 경로를 평가하는 수동 프로브다. */
@EnabledIfEnvironmentVariable(named = "TELME_ROUTING_EVAL", matches = "true")
class RoutingEvaluationProbe {
    private static final String CUSTOM_INPUT = System.getenv("TELME_ROUTING_INPUT");
    private static final List<Path> INPUTS = CUSTOM_INPUT == null || CUSTOM_INPUT.isBlank()
            ? List.of(
                    Path.of("scripts/data/eval_questions_130.json"),
                    Path.of("scripts/data/eval_questions_supplement_50.json"))
            : List.of(Path.of(CUSTOM_INPUT));
    private static final Path LABELS = Path.of("scripts/data/eval_routing_intent_labels.json");
    private static final Path OUTPUT = Path.of(
            System.getenv().getOrDefault("TELME_ROUTING_OUT", "build/tmp/routing-eval-180.json"));
    private static final String MODEL = "exaone3.5:7.8b";
    // 라우팅 호출별로 보낼 주소. 호출 하나씩 모델을 바꿔 비교할 때 쓴다(TELME-175). 모두 Ollama API 형식이어야 한다.
    // 지정하지 않은 호출은 BASE_URL로 보낸다
    private static final String BASE_URL =
            System.getenv().getOrDefault("TELME_ROUTING_BASE_URL", "http://localhost:11434");
    // 의도 분류는 1단계 비교로 gpt-oss(Bedrock 라우터)로 정했다
    private static final String INTENT_URL =
            System.getenv().getOrDefault("TELME_ROUTING_INTENT_URL", "http://127.0.0.1:11435");
    // 요청 개수 판정은 2단계 비교로 gpt-oss로 정했다
    private static final String INVENTORY_URL =
            System.getenv().getOrDefault("TELME_ROUTING_INVENTORY_URL", "http://127.0.0.1:11435");
    // 검색어 검증은 3단계 비교로 gpt-oss로 정했다
    private static final String FAITHFULNESS_URL =
            System.getenv().getOrDefault("TELME_ROUTING_FAITHFULNESS_URL", "http://127.0.0.1:11435");
    // 후속 분석은 4단계 비교로 gpt-oss로 정했다
    private static final String FOLLOWUP_URL =
            System.getenv().getOrDefault("TELME_ROUTING_FOLLOWUP_URL", "http://127.0.0.1:11435");
    // inventory: 라우팅 흐름 대신 요청 개수 판정 호출만 문항마다 직접 부른다(TELME-175 2단계).
    // 이 호출의 입력은 질문 하나뿐이라, 의도 분류가 나눈 문항에만 도는 앱 흐름보다 넓게 시험할 수 있다
    private static final boolean INVENTORY_STEP =
            "inventory".equals(System.getenv().getOrDefault("TELME_ROUTING_STEP", "intent"));
    // faithfulness-cases: 1단계 의도 분류 출력(고정 입력)에서 검색어 검증 호출이 받을 입력을 앱 코드로 만든다.
    // faithfulness: 그 입력(정답 라벨을 붙인 파일)으로 검색어 검증 호출만 부른다(TELME-175 3단계)
    private static final String STEP = System.getenv().getOrDefault("TELME_ROUTING_STEP", "intent");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void evaluateCurrentRoutingWithLocalModel() throws Exception {
        Map<String, String> replay = new LinkedHashMap<>();
        String replayFile = System.getenv("TELME_ROUTING_REPLAY");
        if (replayFile != null) {
            JsonNode replayRoot = mapper.readTree(Path.of(replayFile).toFile());
            JsonNode replayRows = replayRoot.isArray() ? replayRoot : replayRoot.path("records");
            for (JsonNode row : replayRows) {
                replay.put(row.path("user_prompt").asText(row.path("question").asText()),
                        row.path("model_response").asText());
            }
        }
        Map<String, String> replayResponses = replayFile == null ? null : replay;
        List<JsonNode> cases = loadCases();
        Map<String, String> expectedIntents = loadExpectedIntents(cases);

        // 같은 평가셋을 여러 회 돈다. 회차마다 클라이언트·서비스를 따로 만들어 동시에 돌리고,
        // 결과는 {"<접두어>-1": 결과, "<접두어>-2": ...} 파일 하나로 쓴다(1회면 기존 형식 그대로)
        int repeats = Integer.parseInt(System.getenv().getOrDefault("TELME_ROUTING_REPEATS", "1"));
        if (INVENTORY_STEP) {
            runRepeated(repeats, name -> buildInventoryResult(runInventoryOnce(cases, name)));
            return;
        }
        if ("faithfulness-cases".equals(STEP)) {
            writeJson(buildFaithfulnessCases(cases));
            System.out.println("[RoutingEval] saved " + OUTPUT.toAbsolutePath());
            return;
        }
        if ("followup".equals(STEP)) {
            runRepeated(repeats, name -> buildFollowupResult(runFollowupOnce(cases, name)));
            return;
        }
        if ("faithfulness".equals(STEP)) {
            runRepeated(repeats, name -> buildFaithfulnessResult(runFaithfulnessOnce(cases, name)));
            return;
        }
        if (repeats <= 1) {
            runOnce(cases, expectedIntents, replayResponses, "", records -> writeJson(buildResult(records)));
            System.out.println("[RoutingEval] saved " + OUTPUT.toAbsolutePath());
            return;
        }
        runRepeated(repeats, name -> buildResult(runOnce(cases, expectedIntents, replayResponses, name, records -> { })));
    }

    private interface RunTask {
        Map<String, Object> run(String runName) throws Exception;
    }

    private void runRepeated(int repeats, RunTask task) throws Exception {
        if (repeats <= 1) {
            writeJson(task.run(""));
            System.out.println("[RoutingEval] saved " + OUTPUT.toAbsolutePath());
            return;
        }
        String prefix = System.getenv().getOrDefault("TELME_ROUTING_RUN_PREFIX", "run");
        ExecutorService executor = Executors.newFixedThreadPool(repeats);
        try {
            List<Future<Map<String, Object>>> futures = new ArrayList<>();
            for (int run = 1; run <= repeats; run++) {
                String name = prefix + "-" + run;
                futures.add(executor.submit(() -> task.run(name)));
            }
            Map<String, Object> combined = new LinkedHashMap<>();
            for (int run = 1; run <= repeats; run++) {
                combined.put(prefix + "-" + run, futures.get(run - 1).get());
            }
            writeJson(combined);
        } finally {
            executor.shutdownNow();
        }
        System.out.println("[RoutingEval] saved " + OUTPUT.toAbsolutePath());
    }

    // 앱의 requestInventory를 그대로 불러 같은 요청·응답 검증·개수 보정(SINGLE·COMPARISON은 1)을 거친다.
    // 판정 실패는 앱에서 질문 분해 보류(복합 질문 안내)가 되므로 FAILED로 남긴다
    private List<Map<String, Object>> runInventoryOnce(List<JsonNode> cases, String runName) throws Exception {
        var client = new LocalOllamaClient(mapper, null);
        var service = new QueryRoutingService(
                client, mapper, mock(QueryRoutingRepository.class), mock(ConsultRequestRepository.class),
                new RuleBasedRoutingFallback(), new IntentConverter(mapper), mock(TransactionTemplate.class));
        Method inventory = QueryRoutingService.class.getDeclaredMethod("requestInventory", String.class);
        inventory.setAccessible(true);
        List<Map<String, Object>> records = new ArrayList<>();
        for (JsonNode item : cases) {
            String question = item.path("question").asText();
            List<Integer> acceptable = new ArrayList<>();
            item.path("acceptable_request_counts").forEach(node -> acceptable.add(node.asInt()));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("eval_id", item.path("eval_id").asText());
            row.put("question", question);
            row.put("user_prompt", RoutingQuestionNormalizer.normalize(question));
            row.put("expected_request_count", item.path("expected_request_count").asInt());
            row.put("acceptable_request_counts", acceptable);
            long started = System.nanoTime();
            client.lastResponse = null;
            try {
                var payload = (LlmRequestInventoryPayload) inventory.invoke(service, question);
                row.put("decision", payload.decision().name());
                row.put("request_count", payload.requestCount());
                row.put("count_matches", acceptable.contains(payload.requestCount()));
            } catch (InvocationTargetException exception) {
                row.put("decision", "FAILED");
                row.put("request_count", null);
                row.put("count_matches", false);
                row.put("error", exception.getCause().getClass().getSimpleName() + ": "
                        + exception.getCause().getMessage());
            }
            row.put("elapsed_ms", (System.nanoTime() - started) / 1_000_000);
            row.put("model_response", client.lastResponse);
            records.add(row);
            if (records.size() % 20 == 0) {
                System.out.println("[RoutingEval]" + (runName.isEmpty() ? "" : " " + runName) + " "
                        + records.size() + "/" + cases.size());
            }
        }
        return records;
    }

    private Map<String, Object> buildInventoryResult(List<Map<String, Object>> records) {
        Map<String, Map<String, Integer>> byExpected = new LinkedHashMap<>();
        Map<String, Integer> decisions = new LinkedHashMap<>();
        int matches = 0;
        for (Map<String, Object> row : records) {
            boolean matched = Boolean.TRUE.equals(row.get("count_matches"));
            matches += matched ? 1 : 0;
            String group = (Integer) row.get("expected_request_count") > 1 ? "multiple" : "single";
            Map<String, Integer> counts = byExpected.computeIfAbsent(group, ignored -> new LinkedHashMap<>(
                    Map.of("total", 0, "matches", 0)));
            counts.merge("total", 1, Integer::sum);
            counts.merge("matches", matched ? 1 : 0, Integer::sum);
            decisions.merge(row.get("decision").toString(), 1, Integer::sum);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", records.size());
        summary.put("matches", matches);
        summary.put("by_expected_request_count", byExpected);
        summary.put("decisions", decisions);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("records", records);
        return result;
    }

    private QueryRoutingService routingService(LocalOllamaClient client) {
        return new QueryRoutingService(
                client, mapper, mock(QueryRoutingRepository.class), mock(ConsultRequestRepository.class),
                new RuleBasedRoutingFallback(), new IntentConverter(mapper), mock(TransactionTemplate.class));
    }

    private static Method accessible(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters);
        method.setAccessible(true);
        return method;
    }

    // 1단계 기록의 의도 분류 응답에서 앱과 같은 방식으로 검색어 검증 입력을 만든다.
    // 복합 FAQ는 인용구를 IndependentQuestionPolicy.verifiedQuote로 확인한 하위 질문 묶음,
    // 단일 FAQ는 원문과 다른 검색어 후보(원문 검색 0건일 때의 재검색 후보)다.
    // 업무 대상 규칙(SearchTopicPolicy)이 먼저 거절하거나 인용 근거가 없어 복합 안내로 끝나면 모델을 부르지 않으므로 skipped로 남긴다
    private List<Map<String, Object>> buildFaithfulnessCases(List<JsonNode> records) throws Exception {
        var service = routingService(new LocalOllamaClient(mapper, null));
        Class<?> quotePolicy = Class.forName("com.telme.intent.service.IndependentQuestionPolicy");
        Method verifiedQuote = accessible(quotePolicy, "verifiedQuote", LlmRoutingPayload.SubQueryPayload.class, String.class);
        Method newTopic = accessible(Class.forName("com.telme.intent.service.SearchTopicPolicy"),
                "introducesNewTopic", String.class, String.class);
        Method quoteKey = accessible(RoutingQuestionNormalizer.class, "quoteKey", String.class);
        Method previousSubject = accessible(QueryRoutingService.class, "previousSubject", String.class, String.class);
        List<Map<String, Object>> cases = new ArrayList<>();
        for (JsonNode record : records) {
            JsonNode response;
            try {
                response = mapper.readTree(record.path("model_response").asText());
            } catch (Exception exception) {
                continue;
            }
            List<JsonNode> subs = new ArrayList<>();
            response.path("subQueries").forEach(subs::add);
            if (!"FAQ".equals(response.path("intent").asText()) || subs.isEmpty()) {
                continue;
            }
            String question = record.path("question").asText();
            String resolved = record.path("resolved_question").asText(null);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("eval_id", record.path("eval_id").asText());
            item.put("expected_intent", "FAQ");
            item.put("question", question);
            item.put("previous_subject", resolved == null ? "" : previousSubject.invoke(service, resolved, question));
            List<Map<String, Object>> queries = new ArrayList<>();
            if (subs.size() > 1) {
                item.put("path", "compound");
                try {
                    for (JsonNode node : subs) {
                        var sub = new LlmRoutingPayload.SubQueryPayload((short) node.path("order").asInt(),
                                ConsultRequest.Intent.FAQ, node.path("queryText").asText(), Map.of(),
                                node.path("requestQuote").asText(null));
                        Map<String, Object> query = new LinkedHashMap<>();
                        query.put("quote", verifiedQuote.invoke(null, sub, question));
                        query.put("query", sub.queryText());
                        queries.add(query);
                    }
                } catch (InvocationTargetException exception) {
                    item.put("skipped", "인용 근거 없음(복합 질문 안내로 끝남)");
                    queries.clear();
                }
            } else {
                String candidate = subs.getFirst().path("queryText").asText();
                if (quoteKey.invoke(null, candidate).equals(quoteKey.invoke(null, question))) {
                    continue;
                }
                item.put("path", resolved == null ? "refined" : "restored");
                if (resolved != null) {
                    // 이전 발언을 복원한 단일 질문은 재검색 검증과 경로가 달라(groundedSingleFaqQuery) 여기서 재지 않는다
                    item.put("skipped", "맥락 복원 단일 질문(재검색 검증과 경로가 다름)");
                } else if ((boolean) newTopic.invoke(null, question, candidate)) {
                    item.put("skipped", "업무 대상 규칙이 먼저 거절(모델 호출 없음)");
                }
                Map<String, Object> query = new LinkedHashMap<>();
                query.put("quote", question);
                query.put("query", candidate);
                queries.add(query);
            }
            item.put("sub_queries", queries);
            cases.add(item);
        }
        return cases;
    }

    // 앱의 findUnsafeFaqQueries를 그대로 부른다. 앱은 응답 형식이 틀리면 모두 unsafe로 보므로,
    // 실패 여부는 원 응답을 따로 확인해 failed로 남긴다
    private List<Map<String, Object>> runFaithfulnessOnce(List<JsonNode> cases, String runName) throws Exception {
        var client = new LocalOllamaClient(mapper, null);
        var service = routingService(client);
        Method findUnsafe = accessible(QueryRoutingService.class, "findUnsafeFaqQueries",
                LlmRoutingPayload.class, String.class, String.class);
        List<Map<String, Object>> records = new ArrayList<>();
        for (JsonNode item : cases) {
            if (item.hasNonNull("skipped") || item.path("sub_queries").isEmpty()) {
                continue;
            }
            String question = item.path("question").asText();
            List<LlmRoutingPayload.SubQueryPayload> subs = new ArrayList<>();
            List<Boolean> gold = new ArrayList<>();
            short order = 1;
            for (JsonNode node : item.path("sub_queries")) {
                subs.add(new LlmRoutingPayload.SubQueryPayload(order++, ConsultRequest.Intent.FAQ,
                        node.path("query").asText(), Map.of(), node.path("quote").asText()));
                // gold_unsafe가 null이면 판단이 갈리는 검색어라 어느 판정이든 맞은 것으로 본다
                JsonNode label = node.path("gold_unsafe");
                gold.add(label.isBoolean() ? label.booleanValue() : null);
            }
            var payload = new LlmRoutingPayload(QueryRouting.Intent.FAQ, BigDecimal.ONE, question, Map.of(), subs);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("case_id", item.path("case_id").asText(item.path("eval_id").asText()));
            row.put("eval_id", item.path("eval_id").asText());
            row.put("path", item.path("path").asText());
            row.put("question", question);
            row.put("previous_subject", item.path("previous_subject").asText(""));
            row.put("sub_queries", mapper.convertValue(item.path("sub_queries"), List.class));
            row.put("gold_unsafe", gold);
            long started = System.nanoTime();
            client.lastResponse = null;
            @SuppressWarnings("unchecked")
            List<Boolean> unsafe = (List<Boolean>) findUnsafe.invoke(service, payload, question,
                    item.path("previous_subject").asText(""));
            row.put("unsafe", unsafe);
            row.put("failed", !validFaithfulnessResponse(client.lastResponse, subs.size()));
            List<Boolean> matches = new ArrayList<>();
            for (int index = 0; index < gold.size(); index++) {
                matches.add(gold.get(index) == null || gold.get(index).equals(unsafe.get(index)));
            }
            row.put("matches", matches);
            row.put("elapsed_ms", (System.nanoTime() - started) / 1_000_000);
            row.put("model_response", client.lastResponse);
            records.add(row);
            if (records.size() % 20 == 0) {
                System.out.println("[RoutingEval]" + (runName.isEmpty() ? "" : " " + runName) + " "
                        + records.size() + "/" + cases.size());
            }
        }
        return records;
    }

    private boolean validFaithfulnessResponse(String response, int size) {
        try {
            JsonNode root = mapper.readTree(response);
            JsonNode unsafe = root.path("unsafe");
            if (!root.isObject() || root.size() != 1 || !unsafe.isArray() || unsafe.size() != size) {
                return false;
            }
            for (JsonNode value : unsafe) {
                if (!value.isBoolean()) {
                    return false;
                }
            }
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private Map<String, Object> buildFaithfulnessResult(List<Map<String, Object>> records) {
        Map<String, Map<String, Integer>> byGold = new LinkedHashMap<>();
        int queries = 0;
        int matches = 0;
        int failed = 0;
        for (Map<String, Object> row : records) {
            failed += Boolean.TRUE.equals(row.get("failed")) ? 1 : 0;
            @SuppressWarnings("unchecked")
            List<Boolean> gold = (List<Boolean>) row.get("gold_unsafe");
            @SuppressWarnings("unchecked")
            List<Boolean> matched = (List<Boolean>) row.get("matches");
            for (int index = 0; index < gold.size(); index++) {
                queries++;
                matches += matched.get(index) ? 1 : 0;
                String group = gold.get(index) == null ? "gold_either" : gold.get(index) ? "gold_unsafe" : "gold_safe";
                Map<String, Integer> counts = byGold.computeIfAbsent(group,
                        ignored -> new LinkedHashMap<>(Map.of("total", 0, "matches", 0)));
                counts.merge("total", 1, Integer::sum);
                counts.merge("matches", matched.get(index) ? 1 : 0, Integer::sum);
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("cases", records.size());
        summary.put("queries", queries);
        summary.put("matches", matches);
        summary.put("by_gold", byGold);
        summary.put("failed_cases", failed);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("records", records);
        return result;
    }

    // 되묻기 답변의 후속 분석 호출(앱의 requestFollowUpAnalysis)만 부른다(TELME-175 4단계).
    // 앱은 이 결과를 되묻는 중인 조건으로 거르고, 조건이 하나도 없으면 규칙으로 한 번 더 읽는다. 여기서는 모델 출력 자체를 남기고
    // 채점은 score_followup_step.py가 한다. 응답을 읽지 못하면 앱은 규칙 분석으로 넘어가므로 failed로 남긴다
    private List<Map<String, Object>> runFollowupOnce(List<JsonNode> cases, String runName) throws Exception {
        var client = new LocalOllamaClient(mapper, null);
        var service = routingService(client);
        Method analyze = accessible(QueryRoutingService.class, "requestFollowUpAnalysis", String.class, java.util.Set.class);
        List<Map<String, Object>> records = new ArrayList<>();
        for (JsonNode item : cases) {
            java.util.Set<String> keys = new java.util.LinkedHashSet<>();
            item.path("pending_keys").forEach(node -> keys.add(node.asText()));
            String reply = item.path("reply").asText();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("case_id", item.path("case_id").asText());
            row.put("pending_keys", new ArrayList<>(keys));
            row.put("reply", reply);
            row.put("user_prompt", RoutingPromptTemplates.followUpUserPrompt(keys, reply));
            long started = System.nanoTime();
            client.lastResponse = null;
            // 앱은 업무를 묻거나 지역을 묻는 중 업무 이름만 짧게 답하면 모델을 부르지 않고 규칙으로 읽는다
            String ruleServiceType = new RuleBasedRoutingFallback().serviceTypeOfShortReply(reply);
            if (ruleServiceType != null && (keys.contains("serviceType") || keys.contains("location"))) {
                row.put("method", "RULE");
                row.put("response_type", "CONDITION_RESPONSE");
                row.put("conditions", List.of(Map.of("key", "serviceType", "status", "FILLED", "value", ruleServiceType)));
                row.put("failed", false);
                row.put("elapsed_ms", 0L);
                row.put("model_response", null);
                records.add(row);
                continue;
            }
            row.put("method", "LLM");
            try {
                var payload = (com.telme.intent.dto.res.LlmFollowUpPayload) analyze.invoke(service, reply, keys);
                row.put("response_type", payload.responseType().name());
                row.put("conditions", mapper.convertValue(payload.conditions(), List.class));
                row.put("failed", false);
            } catch (InvocationTargetException exception) {
                row.put("response_type", null);
                row.put("conditions", List.of());
                row.put("failed", true);
                row.put("error", exception.getCause().getClass().getSimpleName() + ": " + exception.getCause().getMessage());
            }
            row.put("elapsed_ms", (System.nanoTime() - started) / 1_000_000);
            row.put("model_response", client.lastResponse);
            records.add(row);
            if (records.size() % 20 == 0) {
                System.out.println("[RoutingEval]" + (runName.isEmpty() ? "" : " " + runName) + " "
                        + records.size() + "/" + cases.size());
            }
        }
        return records;
    }

    private Map<String, Object> buildFollowupResult(List<Map<String, Object>> records) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("cases", records.size());
        summary.put("failed_cases", records.stream().filter(row -> Boolean.TRUE.equals(row.get("failed"))).count());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("records", records);
        return result;
    }

    private interface RecordsListener {
        void onRecord(List<Map<String, Object>> records) throws Exception;
    }

    private List<Map<String, Object>> runOnce(List<JsonNode> cases, Map<String, String> expectedIntents,
            Map<String, String> replay, String runName, RecordsListener listener) throws Exception {
        var client = new LocalOllamaClient(mapper, replay);
        var routings = mock(QueryRoutingRepository.class);
        var requests = mock(ConsultRequestRepository.class);
        var transaction = mock(TransactionTemplate.class);
        when(routings.findByMessage_MessageId(any())).thenReturn(Optional.empty());
        when(routings.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(requests.save(any())).thenAnswer(call -> call.getArgument(0));
        when(transaction.execute(any())).thenAnswer(call ->
                ((TransactionCallback<?>) call.getArgument(0)).doInTransaction(null));
        var service = new QueryRoutingService(
                client, mapper, routings, requests, new RuleBasedRoutingFallback(),
                new IntentConverter(mapper), transaction);

        List<Map<String, Object>> records = new ArrayList<>();
        for (JsonNode item : cases) {
            String id = item.path("eval_id").asText();
            String type = item.path("type").asText();
            String question = item.path("question").asText();
            String summary = item.path("summary").asText(null);
            ChatMessage message = ChatMessage.builder()
                    .messageId((long) records.size() + 1)
                    .session(ChatSession.builder().sessionId(1L).build())
                    .role(ChatMessage.Role.USER)
                    .messageType(ChatMessage.MessageType.QUESTION)
                    .content(question)
                    .build();
            ChatContext context = summary == null ? null
                    : new ChatContext(1L, message.getMessageId(), summary, List.of(), question, 0);
            // 앱은 문맥 해석이 이전 고객 발언을 연결했을 때만 그 발언(history)과 복원 질문을 라우팅에 넘긴다.
            // previous_questions + context_linked=true면 그 경로를, false면 질문만 넘기는 경로를 그대로 재현한다
            String resolvedQuestion = null;
            List<String> previous = new ArrayList<>();
            item.path("previous_questions").forEach(node -> previous.add(node.asText()));
            if (!previous.isEmpty() && item.path("context_linked").asBoolean(false)) {
                List<ChatContextMessage> history = new ArrayList<>();
                for (int index = 0; index < previous.size(); index++) {
                    history.add(new ChatContextMessage(100_000L + index, index + 1, ChatMessage.Role.USER,
                            ChatMessage.MessageType.QUESTION, previous.get(index), null));
                }
                context = new ChatContext(1L, message.getMessageId(), null, history, question, 0);
                resolvedQuestion = "[대상을 확인할 이전 고객 발언]\n" + String.join("\n", previous)
                        + "\n[현재 후속 질문]\n" + question;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("eval_id", id);
            row.put("type", type);
            row.put("unrelated_kind", item.path("unrelated_kind").asText(null));
            row.put("question", question);
            if (summary != null) {
                row.put("summary", summary);
            }
            if (resolvedQuestion != null) {
                row.put("resolved_question", resolvedQuestion);
            }
            row.put("user_prompt", RoutingPromptTemplates.routingUserPrompt(context, question));
            String expectedIntent = expectedIntents.get(id);
            row.put("expected_intent", expectedIntent);
            long started = System.nanoTime();
            try {
                client.intentResponse = null;
                IntentRouteResponse route = resolvedQuestion == null
                        ? service.routeSingleConsult(message, context)
                        : service.routeSingleConsult(message, context, resolvedQuestion);
                row.put("intent", route.intent().name());
                row.put("intent_matches", route.intent().name().equals(expectedIntent));
                row.put("compound_guidance", false);
                row.put("method", route.method().name());
                row.put("confidence", route.confidence());
                row.put("search_query", route.subQueries().isEmpty()
                        ? null : route.subQueries().getFirst().queryText());
                row.put("refined_query", route.refinedQuery());
            } catch (UnsupportedCompoundQuestionException exception) {
                // 복합 질문 안내는 BOTH뿐 아니라 하위 질문 intent가 섞인 경우에도 나온다.
                // 안내 여부와 분류 정확도를 섞지 않도록, 채점은 모델이 실제로 낸 intent로 한다
                String modelIntent = modelIntent(client.intentResponse);
                row.put("intent", modelIntent);
                row.put("intent_matches", modelIntent.equals(expectedIntent));
                row.put("compound_guidance", true);
                row.put("sub_intents", modelSubIntents(client.intentResponse));
            } catch (RuntimeException exception) {
                row.put("intent", "ERROR");
                row.put("intent_matches", false);
                row.put("compound_guidance", false);
                row.put("error", exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
            row.put("elapsed_ms", (System.nanoTime() - started) / 1_000_000);
            row.put("model_response", client.intentResponse);
            // 보정(규칙·검색 재확인) 전 모델이 직접 낸 의도
            row.put("model_intent", modelIntent(client.intentResponse));
            records.add(row);
            listener.onRecord(records);
            if (records.size() % 10 == 0) {
                System.out.println("[RoutingEval]" + (runName.isEmpty() ? "" : " " + runName) + " "
                        + records.size() + "/" + cases.size());
            }
        }
        return records;
    }

    private List<JsonNode> loadCases() throws Exception {
        List<JsonNode> cases = new ArrayList<>();
        for (Path input : INPUTS) {
            mapper.readTree(input.toFile()).forEach(cases::add);
        }
        return cases;
    }

    private Map<String, String> loadExpectedIntents(List<JsonNode> cases) throws Exception {
        Map<String, String> labels = new LinkedHashMap<>();
        for (JsonNode group : mapper.readTree(LABELS.toFile()).path("groups")) {
            int from = evalNumber(group.path("from").asText());
            int to = evalNumber(group.path("to").asText());
            String expectedIntent = group.path("expected_intent").asText();
            for (int number = from; number <= to; number++) {
                String evalId = "EVAL-%03d".formatted(number);
                if (labels.put(evalId, expectedIntent) != null) {
                    throw new IllegalStateException("Duplicate routing label: " + evalId);
                }
            }
        }
        for (JsonNode item : cases) {
            if (item.hasNonNull("expected_intent")) {
                labels.put(item.path("eval_id").asText(), item.path("expected_intent").asText());
            }
        }
        List<String> missing = cases.stream()
                .map(item -> item.path("eval_id").asText())
                .filter(evalId -> !labels.containsKey(evalId))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing routing labels: " + missing);
        }
        return labels;
    }

    private int evalNumber(String evalId) {
        return Integer.parseInt(evalId.substring("EVAL-".length()));
    }

    private void writeJson(Object result) throws Exception {
        Files.createDirectories(OUTPUT.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(OUTPUT.toFile(), result);
    }

    private Map<String, Object> buildResult(List<Map<String, Object>> records) {
        Map<String, Map<String, Integer>> byExpectedIntent = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> confusion = new LinkedHashMap<>();
        Map<String, Integer> compoundGuidance = new LinkedHashMap<>();
        int matches = 0;
        for (Map<String, Object> row : records) {
            String expectedIntent = row.get("expected_intent").toString();
            String actualIntent = row.get("intent").toString();
            boolean matched = Boolean.TRUE.equals(row.get("intent_matches"));
            Map<String, Integer> counts = byExpectedIntent.computeIfAbsent(expectedIntent, ignored -> {
                Map<String, Integer> initial = new LinkedHashMap<>();
                initial.put("total", 0);
                initial.put("matches", 0);
                return initial;
            });
            counts.put("total", counts.get("total") + 1);
            if (matched) {
                matches++;
                counts.put("matches", counts.get("matches") + 1);
            }
            confusion.computeIfAbsent(expectedIntent, ignored -> new LinkedHashMap<>())
                    .merge(actualIntent, 1, Integer::sum);
            if (Boolean.TRUE.equals(row.get("compound_guidance"))) {
                compoundGuidance.merge(expectedIntent, 1, Integer::sum);
            }
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", records.size());
        summary.put("matches", matches);
        summary.put("by_expected_intent", byExpectedIntent);
        summary.put("confusion", confusion);
        // 기대 intent별로 복합 질문 안내가 나간 건수. 분류 정확도(matches)와 따로 본다
        summary.put("compound_guidance_by_expected_intent", compoundGuidance);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("records", records);
        return result;
    }

    // 복합 질문 안내로 끝나면 라우팅 결과가 없으므로 모델 원 응답에서 intent를 읽는다
    private String modelIntent(String response) {
        try {
            String intent = mapper.readTree(response).path("intent").asText("");
            return intent.isBlank() ? "PARSE_ERROR" : intent;
        } catch (Exception exception) {
            return "PARSE_ERROR";
        }
    }

    private List<String> modelSubIntents(String response) {
        List<String> intents = new ArrayList<>();
        try {
            mapper.readTree(response).path("subQueries")
                    .forEach(sub -> intents.add(sub.path("intent").asText("")));
        } catch (Exception ignored) {
            // 원 응답을 읽지 못하면 빈 목록으로 남긴다. intent는 PARSE_ERROR로 이미 불일치 처리된다
        }
        return intents;
    }

    private static final class LocalOllamaClient implements LlmClient {
        private final ObjectMapper mapper;
        private final Map<String, String> replay;
        // 비교용 라우터(judge_router.py)는 HTTP/1.0 서버라 HTTP/2 업그레이드 시도 없이 HTTP/1.1로 보낸다
        private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        private String lastResponse;
        private String intentResponse;

        private LocalOllamaClient(ObjectMapper mapper, Map<String, String> replay) {
            this.mapper = mapper;
            this.replay = replay;
        }

        @Override
        public String generate(LlmRequest request) {
            lastResponse = null;
            if (replay != null) {
                lastResponse = replay.get(request.userPrompt());
                if (lastResponse == null) {
                    throw new IllegalStateException("Missing replay response: " + request.userPrompt());
                }
                if (RoutingPromptTemplates.ROUTING_SYSTEM_PROMPT.equals(request.systemPrompt())) {
                    intentResponse = lastResponse;
                }
                return lastResponse;
            }
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", MODEL);
                body.put("stream", false);
                body.put("format", "json");
                body.put("messages", List.of(
                        Map.of("role", "system", "content", request.systemPrompt()),
                        Map.of("role", "user", "content", request.userPrompt())));
                body.put("options", Map.of(
                        "temperature", request.temperature(),
                        "num_predict", request.maxTokens(),
                        "num_ctx", 8192));
                boolean intentCall = RoutingPromptTemplates.ROUTING_SYSTEM_PROMPT.equals(request.systemPrompt());
                HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(urlFor(request.systemPrompt()) + "/api/chat"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofMinutes(3))
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .build();
                HttpResponse<String> response = send(httpRequest);
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Ollama HTTP " + response.statusCode() + ": " + response.body());
                }
                lastResponse = mapper.readTree(response.body()).path("message").path("content").asText();
                if (intentCall) {
                    intentResponse = lastResponse;
                }
                return lastResponse;
            } catch (Exception exception) {
                Throwable root = exception;
                while (root.getCause() != null) {
                    root = root.getCause();
                }
                throw new IllegalStateException("Ollama routing probe failed: "
                        + root.getClass().getSimpleName() + ": " + root.getMessage(), exception);
            }
        }

        // 연결이 끊겨 요청이 모델까지 가지 못한 경우(IOException)만 다시 보낸다. 모델 응답은 다시 받지 않는다
        private HttpResponse<String> send(HttpRequest request) throws Exception {
            for (int attempt = 1; ; attempt++) {
                try {
                    return http.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (java.io.IOException exception) {
                    if (attempt == 3) {
                        throw exception;
                    }
                }
            }
        }

        private static String urlFor(String systemPrompt) {
            if (RoutingPromptTemplates.ROUTING_SYSTEM_PROMPT.equals(systemPrompt)) {
                return INTENT_URL;
            }
            if (RoutingPromptTemplates.REQUEST_INVENTORY_PROMPT.equals(systemPrompt)) {
                return INVENTORY_URL;
            }
            if (RoutingPromptTemplates.FAQ_QUERY_FAITHFULNESS_PROMPT.equals(systemPrompt)
                    || RoutingPromptTemplates.FAQ_SINGLE_QUERY_FAITHFULNESS_PROMPT.equals(systemPrompt)) {
                return FAITHFULNESS_URL;
            }
            if (RoutingPromptTemplates.FOLLOW_UP_SYSTEM_PROMPT.equals(systemPrompt)) {
                return FOLLOWUP_URL;
            }
            return BASE_URL;
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            throw new UnsupportedOperationException("Routing uses generate");
        }
    }
}
