package com.telme.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.config.ChatQuestionResolutionProperties.Mode;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatContext;
import com.telme.chat.service.ChatContextMessage;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ChatQuestionResolver;
import com.telme.chat.service.ExecutionTrace;
import com.telme.llm.config.BedrockProperties;
import com.telme.llm.config.LlmProperties;
import com.telme.llm.converter.BedrockRequestConverter;
import com.telme.llm.converter.OllamaRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.BedrockClient;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.llm.service.OllamaClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * TELME-165 문맥 판정을 gpt-oss로 바꿀 때의 실제 경로(앱의 BedrockClient)와 측정용 중계기(judge_router.py) 경로로
 * 같은 평가 문장(scripts/context-resolution-evaluation/cases.json)에 돌려 판정 결과를 비교한다. 모델 판정만 보므로 앱·DB는 띄우지 않는다.
 *
 * 실행: CONTEXT_PROBE=true CONTEXT_PROBE_OUT=<결과 jsonl> [CONTEXT_PROBE_CLIENTS=bedrock,router] [CONTEXT_PROBE_REPEATS=3]
 *       [CONTEXT_PROBE_KEYS=cases,...] [CONTEXT_PROBE_ROUTER_URL=http://127.0.0.1:11435] [CONTEXT_PROBE_OLLAMA_MODEL=ab-chat]
 *       [CONTEXT_PROBE_GUARDS=true] [LLM_BEDROCK_REGION=ap-northeast-1]
 *       ./gradlew test --tests '*ContextResolutionClientProbe'
 * Bedrock 자격 증명은 AWS 기본 자격 증명(AWS_PROFILE 등)을 쓴다. 채점은 정답 관계와 이전 발언 번호가 모두 맞아야 일치다.
 */
@EnabledIfEnvironmentVariable(named = "CONTEXT_PROBE", matches = "true")
class ContextResolutionClientProbe {
    private static final List<String> DEFAULT_KEYS = List.of("cases", "accuracyCases", "unseenCases", "costCases",
            "gateCases", "reviewCases", "confirmationCases", "evidenceValidationCases", "sourceSelectionCases",
            "blindCases");
    private static final long INPUT_MESSAGE_ID = 999L;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void compareClients() throws Exception {
        JsonNode fixtures = mapper.readTree(Files.readString(Path.of("scripts/context-resolution-evaluation/cases.json")));
        List<String> keys = list("CONTEXT_PROBE_KEYS", DEFAULT_KEYS);
        List<String> clients = list("CONTEXT_PROBE_CLIENTS", List.of("bedrock", "router"));
        int repeats = Integer.parseInt(System.getenv().getOrDefault("CONTEXT_PROBE_REPEATS", "3"));
        Map<String, LlmClient> bases = new LinkedHashMap<>();
        for (String client : clients) {
            bases.put(client, "bedrock".equals(client) ? bedrockClient() : routerClient());
        }
        ExecutorService pool = Executors.newFixedThreadPool(12);
        List<Future<Map<String, Object>>> rows = new ArrayList<>();
        for (String client : clients) {
            for (int run = 1; run <= repeats; run++) {
                for (String key : keys) {
                    for (JsonNode fixture : fixtures.path(key)) {
                        int current = run;
                        rows.add(pool.submit(() -> judge(client, bases.get(client), current, key, fixture)));
                    }
                }
            }
        }
        List<String> lines = new ArrayList<>();
        for (Future<Map<String, Object>> row : rows) {
            lines.add(mapper.writeValueAsString(row.get()));
        }
        pool.shutdown();
        Path out = Path.of(System.getenv("CONTEXT_PROBE_OUT"));
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.write(out, lines);
    }

    private Map<String, Object> judge(String clientName, LlmClient base, int run, String key, JsonNode fixture) {
        String question = fixture.path("question").asText();
        List<ChatContextMessage> history = new ArrayList<>();
        int sequence = 1;
        for (JsonNode text : fixture.path("history")) {
            history.add(new ChatContextMessage(100L + sequence, sequence, ChatMessage.Role.USER,
                    ChatMessage.MessageType.QUESTION, text.asText(), null));
            sequence++;
        }
        List<Map<String, Object>> calls = new ArrayList<>();
        LlmClient recording = new LlmClient() {
            @Override
            public String generate(LlmRequest request) {
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("promptVersion", request.promptVersion());
                call.put("maxTokens", request.maxTokens());
                long started = System.nanoTime();
                try {
                    String response = base.generate(request);
                    call.put("response", response);
                    return response;
                } catch (RuntimeException failure) {
                    call.put("error", failure.toString());
                    throw failure;
                } finally {
                    call.put("ms", (System.nanoTime() - started) / 1_000_000);
                    synchronized (calls) {
                        calls.add(call);
                    }
                }
            }

            @Override
            public void stream(LlmRequest request, LlmStreamHandler handler) {
                throw new UnsupportedOperationException();
            }
        };
        // 흔들림 보완(chat.question-resolution.stability-guards)은 CONTEXT_PROBE_GUARDS로 고른다. 기본은 켬
        boolean guards = Boolean.parseBoolean(System.getenv().getOrDefault("CONTEXT_PROBE_GUARDS", "true"));
        var resolver = new ChatQuestionResolver(recording, mapper, ExecutionTrace.noop(), Mode.REGEX_GATED, guards);
        var context = new ChatContext(1L, INPUT_MESSAGE_ID, null, history, question, 100);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("client", clientName);
        row.put("run", run);
        row.put("key", key);
        row.put("id", fixture.path("id").asText());
        row.put("expectedRelation", fixture.path("expectedRelation").asText());
        row.put("expectedSourceIndexes", mapper.convertValue(fixture.path("expectedSourceIndexes"), List.class));
        try {
            var result = resolver.resolve(new ChatProcessingCommand(1L, 1L, INPUT_MESSAGE_ID, question), context);
            String relation = result.needsClarification() ? "CLARIFICATION_REQUIRED"
                    : result.sourceMessageIds().isEmpty() ? "SELF_CONTAINED" : "HISTORY_DEPENDENT";
            row.put("relation", relation);
            row.put("sourceIndexes", result.sourceMessageIds().stream().map(id -> (int) (id - 100)).toList());
        } catch (RuntimeException failure) {
            // 시간 초과·연결 실패처럼 확인 안내로 바꾸지 않는 실패. 채점에서는 틀린 것으로 센다
            row.put("relation", "ERROR");
            row.put("sourceIndexes", List.of());
            row.put("error", failure.toString());
        }
        row.put("calls", calls);
        return row;
    }

    // 운영 설정(BedrockConfig·application.yml llm.bedrock)과 같은 값. 지역만 평가 계정에 맞춘다
    private static LlmClient bedrockClient() {
        var properties = new BedrockProperties(true,
                System.getenv().getOrDefault("LLM_BEDROCK_REGION", "ap-northeast-1"),
                "openai.gpt-oss-120b-1:0", Duration.ofSeconds(8), "low", 1000, 50, Duration.ofSeconds(1));
        var runtime = BedrockRuntimeClient.builder()
                .region(Region.of(properties.region()))
                .httpClientBuilder(Apache5HttpClient.builder()
                        .maxConnections(properties.maxConnections())
                        .connectionAcquisitionTimeout(properties.connectionAcquireTimeout()))
                .overrideConfiguration(config -> config
                        .apiCallTimeout(properties.timeout())
                        .retryStrategy(AwsRetryStrategy.doNotRetry()))
                .build();
        return new BedrockClient(runtime, new BedrockRequestConverter(properties));
    }

    // 문맥 해석 평가가 쓰던 경로. 앱의 OllamaClient가 중계기로 보내고 중계기가 Bedrock gpt-oss로 넘긴다
    private LlmClient routerClient() {
        // 중계기는 모델 이름을 보지 않는다. Ollama(EXAONE)로 바로 보낼 때는 CONTEXT_PROBE_OLLAMA_MODEL로 모델을 준다
        var properties = new LlmProperties(System.getenv().getOrDefault("CONTEXT_PROBE_OLLAMA_MODEL", "ab-chat"),
                Duration.ofSeconds(5), Duration.ofSeconds(120), 8192);
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeout());
        factory.setReadTimeout(properties.readTimeout());
        var rest = RestClient.builder()
                .baseUrl(System.getenv().getOrDefault("CONTEXT_PROBE_ROUTER_URL", "http://127.0.0.1:11435"))
                .requestFactory(factory).build();
        return new OllamaClient(rest, new OllamaRequestConverter(properties), mapper);
    }

    private static List<String> list(String name, List<String> fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : List.of(value.split(","));
    }
}
