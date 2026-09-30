package com.telme.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.service.ChatContext;
import com.telme.consult.repository.ConsultRequestRepository;
import com.telme.intent.converter.IntentConverter;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.repository.QueryRoutingRepository;
import com.telme.intent.service.QueryRoutingService;
import com.telme.intent.service.RuleBasedRoutingFallback;
import com.telme.intent.service.RoutingPromptTemplates;
import com.telme.intent.service.UnsupportedCompoundQuestionException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
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
        var client = new LocalOllamaClient(mapper, replayFile == null ? null : replay);
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

        List<JsonNode> cases = loadCases();
        Map<String, String> expectedIntents = loadExpectedIntents(cases);
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
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("eval_id", id);
            row.put("type", type);
            row.put("unrelated_kind", item.path("unrelated_kind").asText(null));
            row.put("question", question);
            if (summary != null) {
                row.put("summary", summary);
            }
            row.put("user_prompt", RoutingPromptTemplates.routingUserPrompt(context, question));
            String expectedIntent = expectedIntents.get(id);
            row.put("expected_intent", expectedIntent);
            long started = System.nanoTime();
            try {
                IntentRouteResponse route = service.routeSingleConsult(message, context);
                row.put("intent", route.intent().name());
                row.put("intent_matches", route.intent().name().equals(expectedIntent));
                row.put("method", route.method().name());
                row.put("confidence", route.confidence());
                row.put("search_query", route.subQueries().isEmpty()
                        ? null : route.subQueries().getFirst().queryText());
                row.put("refined_query", route.refinedQuery());
            } catch (UnsupportedCompoundQuestionException exception) {
                row.put("intent", "COMPOUND_GUIDANCE");
                row.put("intent_matches", "BOTH".equals(expectedIntent));
            } catch (RuntimeException exception) {
                row.put("intent", "ERROR");
                row.put("intent_matches", false);
                row.put("error", exception.getClass().getSimpleName() + ": " + exception.getMessage());
            }
            row.put("elapsed_ms", (System.nanoTime() - started) / 1_000_000);
            row.put("model_response", client.lastResponse);
            records.add(row);
            writeResult(records);
            if (records.size() % 10 == 0) {
                System.out.println("[RoutingEval] " + records.size() + "/" + cases.size());
            }
        }
        System.out.println("[RoutingEval] saved " + OUTPUT.toAbsolutePath());
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

    private void writeResult(List<Map<String, Object>> records) throws Exception {
        Map<String, Map<String, Integer>> byExpectedIntent = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> confusion = new LinkedHashMap<>();
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
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", records.size());
        summary.put("matches", matches);
        summary.put("by_expected_intent", byExpectedIntent);
        summary.put("confusion", confusion);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("records", records);
        Files.createDirectories(OUTPUT.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(OUTPUT.toFile(), result);
    }

    private static final class LocalOllamaClient implements LlmClient {
        private final ObjectMapper mapper;
        private final Map<String, String> replay;
        private final HttpClient http = HttpClient.newHttpClient();
        private String lastResponse;

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
                HttpRequest httpRequest = HttpRequest.newBuilder(URI.create("http://localhost:11434/api/chat"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofMinutes(3))
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                        .build();
                HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Ollama HTTP " + response.statusCode());
                }
                lastResponse = mapper.readTree(response.body()).path("message").path("content").asText();
                return lastResponse;
            } catch (Exception exception) {
                throw new IllegalStateException("Ollama routing probe failed", exception);
            }
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            throw new UnsupportedOperationException("Routing uses generate");
        }
    }
}
