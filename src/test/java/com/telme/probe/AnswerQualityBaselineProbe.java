package com.telme.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.rag.service.AnswerGenerator;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.dto.res.AnswerResult;
import com.telme.rag.exception.AnswerGuardException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 평가 질문으로 답변을 생성하고 질문·검색 근거·답변·실행 설정을 파일로 남긴다.
 * 채점은 하지 않는다. 검색을 고정해 재생하거나 현재 검색 결과를 새로 수집할 수 있다.
 *
 * 실행
 *   TELME_PROBE=true TELME_PROBE_LLM=ollama TELME_PROBE_OUT=.measure/paired-exaone \
 *     ./gradlew test --tests '*AnswerQualityBaselineProbe*'
 *   TELME_PROBE=true TELME_PROBE_ALLOW_PAID=true TELME_PROBE_LLM=bedrock \
 *     TELME_PROBE_REPLAY_FROM=.measure/paired-exaone/<baseline>.json \
 *     TELME_PROBE_OUT=.measure/paired-bedrock ./gradlew test --tests '*AnswerQualityBaselineProbe*'
 * 두 번째 실행은 첫 실행이 저장한 동일 eval_id·질문·검색 근거를 재사용한다.
 * Bedrock은 유료 호출이다. 실행 전에 TELME_PROBE_ALLOW_PAID=true로 명시 동의해야 한다.
 * 자격 증명은 프로세스 환경 변수로만 전달하며 파일에 저장하지 않는다.
 *
 * 출력 경로는 TELME_PROBE_OUT으로 바꿀 수 있다.
 */
@SpringBootTest(properties = {
        "llm.provider=ollama",
        "faq.search-test-api-enabled=false"
})
@Import(AnswerQualityBaselineProbe.ZeroTemperatureConfig.class)
@EnabledIfEnvironmentVariable(named = "TELME_PROBE", matches = "true")
class AnswerQualityBaselineProbe {

    private static final List<String> EVAL_FILES = List.of(
            "scripts/data/eval_questions_130.json",
            "scripts/data/eval_answer_quality_20.json"
    );
    private static final int TOP_K = 3;
    private static final int MAX_TOKENS = 1024;
    private static final String GENERATOR = System.getenv().getOrDefault("TELME_PROBE_LLM", "ollama");
    private static final String GENERATOR_MODEL = resolveGeneratorModel(GENERATOR, System.getenv());
    // 기본은 서비스와 같은 0. 이전 운영값(0.2)과 비교할 때만 TELME_PROBE_TEMP로 바꾼다
    private static final double TEMPERATURE =
            Double.parseDouble(System.getenv().getOrDefault("TELME_PROBE_TEMP", "0"));

    @Autowired
    private FaqSearchService faqSearchService;

    @Autowired
    private AnswerGenerator answerGenerator;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 기준선을_측정한다() throws IOException {
        validatePaidCallOptIn(GENERATOR, Boolean.parseBoolean(
                System.getenv().getOrDefault("TELME_PROBE_ALLOW_PAID", "false")));
        List<EvalCase> cases = loadCases();
        String replayPath = System.getenv("TELME_PROBE_REPLAY_FROM");
        Map<String, List<FaqSearchResponse>> replaySources = replayPath == null || replayPath.isBlank()
                ? null
                : loadReplaySources(Path.of(replayPath), cases);
        StringBuilder out = new StringBuilder();
        Map<String, Integer> tally = new LinkedHashMap<>();
        List<Map<String, Object>> records = new ArrayList<>();
        String stopReason = null;
        String failedEvalId = null;

        for (EvalCase c : cases) {
            Outcome outcome = run(c, replaySources);
            if ("bedrock".equals(GENERATOR) && "GENERATION_ERROR".equals(outcome.status)) {
                // 성공한 유료 호출의 부분 결과와 사용량을 먼저 저장하고, 아래에서 중단한다.
                stopReason = "Bedrock 호출 실패로 추가 과금 방지를 위해 중단했습니다. eval_id="
                        + c.evalId + " error=" + outcome.error;
                failedEvalId = c.evalId;
            }
            tally.merge(outcome.status, 1, Integer::sum);
            append(out, c, outcome);
            records.add(toRecord(c, outcome));
            if (stopReason != null) {
                break;
            }
        }

        out.append(summary(cases.size(), records.size(), tally, replayPath, stopReason));
        Map<String, Object> usage = null;
        if ("bedrock".equals(GENERATOR)) {
            usage = bedrockUsageMetadata(
                    GENERATOR, GENERATOR_MODEL, BedrockLlmClient.INPUT_TOKENS.get(),
                    BedrockLlmClient.OUTPUT_TOKENS.get(), records.size(), failedEvalId, stopReason);
        }
        ProbeArtifacts artifacts = persistArtifacts(
                Path.of(System.getenv().getOrDefault("TELME_PROBE_OUT", ".measure")),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")),
                out.toString(), records, usage, mapper);
        System.out.println("[Probe] 답변 기록 저장: " + artifacts.report().toAbsolutePath());
        System.out.println("[Probe] 채점용 결과 저장: " + artifacts.results().toAbsolutePath());
        System.out.println("[Probe] " + tally);
        System.out.printf("[Probe] generator=%s model=%s temperature=%.2f maxTokens=%d retrieval=%s%n",
                GENERATOR, GENERATOR_MODEL, TEMPERATURE, MAX_TOKENS,
                replayPath == null || replayPath.isBlank() ? "live" : "replay:" + replayPath);
        if ("bedrock".equals(GENERATOR)) {
            System.out.printf("[Probe] Bedrock usage inputTokens=%d outputTokens=%d%n",
                    BedrockLlmClient.INPUT_TOKENS.get(), BedrockLlmClient.OUTPUT_TOKENS.get());
            System.out.println("[Probe] 사용량 메타데이터: " + artifacts.usage().toAbsolutePath());
        }
        if (stopReason != null) {
            throw new IllegalStateException(stopReason);
        }
    }

    private Outcome run(EvalCase c, Map<String, List<FaqSearchResponse>> replaySources) {
        List<FaqSearchResponse> found;
        if (replaySources != null) {
            found = replaySources.get(c.evalId);
            if (found == null) {
                throw new IllegalArgumentException("Replay 입력에 eval_id가 없습니다: " + c.evalId);
            }
        } else {
            try {
                found = faqSearchService.search(new FaqSearchRequest(c.question, TOP_K));
            } catch (RuntimeException e) {
                return new Outcome("SEARCH_ERROR", List.of(), "", reason(e));
            }
        }

        AnswerRequest request = AnswerRequest.builder()
                // 호출 기록을 남기지 않아 측정이 llm_generations를 더럽히지 않는다
                .executionId(null)
                .userQuery(c.question)
                .searchResults(found)
                .build();

        try {
            AnswerResult result = answerGenerator.generate(request, new SilentHandler());
            String status = found.isEmpty() ? "NO_SEARCH" : result.answerBasis().name();
            return new Outcome(status, found, result.answer(), null);
        } catch (RuntimeException e) {
            return new Outcome(failureStatus(e), found, "", reason(e));
        }
    }

    private void append(StringBuilder out, EvalCase c, Outcome o) {
        out.append(c.evalId).append(" [").append(c.type).append("] ")
                .append("검색=").append(o.found.size()).append("건 | ").append(o.status).append('\n');
        out.append("  Q: ").append(c.question).append('\n');
        if (c.risk != null) {
            out.append("  RISK: ").append(c.risk).append('\n');
        }
        out.append("  A: ").append(o.answer.isBlank() ? "(없음)" : o.answer).append('\n');
        if (o.error != null) {
            out.append("  ERR: ").append(o.error).append('\n');
        }
        for (FaqSearchResponse f : o.found) {
            out.append(String.format("      - %.4f | %s%n", f.score(), f.question()));
            out.append("        ").append(f.answer()).append('\n');
        }
        out.append('\n');
    }

    private String summary(
            int requested, int recorded, Map<String, Integer> tally, String replayPath, String stopReason) {
        StringBuilder s = new StringBuilder();
        s.append("=== 답변 측정 (generator ").append(GENERATOR)
                .append(", model ").append(GENERATOR_MODEL)
                .append(", temperature ").append(TEMPERATURE)
                .append(", maxTokens ").append(MAX_TOKENS)
                .append(", retrieval ").append(replayPath == null || replayPath.isBlank() ? "live" : "replay")
                .append(", topK ").append(TOP_K).append(") ===\n");
        s.append("평가셋 ").append(requested).append("건, 저장된 결과 ").append(recorded).append("건\n");
        tally.forEach((k, v) -> s.append(String.format("  %-14s %d%n", k, v)));
        s.append("실행 상태: ").append(stopReason == null ? "완료" : "중단").append('\n');
        if (stopReason != null) {
            s.append("중단 사유: ").append(stopReason).append('\n');
        }
        return s.toString();
    }

    static Map<String, Object> bedrockUsageMetadata(
            String generator,
            String model,
            long inputTokens,
            long outputTokens,
            int recordedCases,
            String failedEvalId,
            String stopReason) {
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("generator", generator);
        usage.put("model", model);
        usage.put("input_tokens", inputTokens);
        usage.put("output_tokens", outputTokens);
        usage.put("recorded_cases", recordedCases);
        usage.put("status", stopReason == null ? "completed" : "aborted");
        usage.put("failed_eval_id", failedEvalId);
        usage.put("stop_reason", stopReason);
        return usage;
    }

    private List<EvalCase> loadCases() throws IOException {
        List<EvalCase> cases = new ArrayList<>();
        for (String file : EVAL_FILES) {
            JsonNode root = mapper.readTree(Path.of(file).toFile());
            for (JsonNode n : root) {
                cases.add(new EvalCase(
                        n.path("eval_id").asText(),
                        n.path("type").asText(),
                        n.path("question").asText(),
                        n.hasNonNull("risk") ? n.get("risk").asText() : null));
            }
        }
        return cases;
    }

    static ProbeArtifacts persistArtifacts(
            Path directory,
            String runId,
            String report,
            List<Map<String, Object>> records,
            Map<String, Object> usageMetadata,
            ObjectMapper mapper) throws IOException {
        Files.createDirectories(directory);
        String prefix = "baseline-" + GENERATOR + "-" + runId;
        Path reportPath = directory.resolve(prefix + ".txt");
        Path resultsPath = directory.resolve(prefix + ".json");
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);
        mapper.writerWithDefaultPrettyPrinter().writeValue(resultsPath.toFile(), records);
        Path usagePath = null;
        if (usageMetadata != null) {
            usagePath = directory.resolve(prefix + ".meta.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(usagePath.toFile(), usageMetadata);
        }
        return new ProbeArtifacts(reportPath, resultsPath, usagePath);
    }

    record ProbeArtifacts(Path report, Path results, Path usage) {
    }

    // 채점 스크립트가 쓰는 형태. 근거는 질문과 답변을 합쳐 한 덩어리로 넘긴다
    private Map<String, Object> toRecord(EvalCase c, Outcome o) {
        List<Map<String, Object>> sources = new ArrayList<>();
        for (FaqSearchResponse f : o.found) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("faqId", f.faqId());
            source.put("slotId", f.slotId());
            source.put("category", f.category());
            source.put("score", f.score());
            source.put("question", f.question());
            source.put("answer", f.answer());
            source.put("version", f.version());
            source.put("updatedAt", f.updatedAt() == null ? null : f.updatedAt().toString());
            source.put("searchRank", f.searchRank());
            source.put("matchedVariant", f.matchedVariant());
            sources.add(source);
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("eval_id", c.evalId);
        record.put("type", c.type);
        record.put("question", c.question);
        record.put("status", o.status);
        record.put("answer", o.answer);
        record.put("error", o.error);
        record.put("risk", c.risk);
        record.put("sources", sources);
        record.put("generator", GENERATOR);
        record.put("generator_model", GENERATOR_MODEL);
        record.put("temperature", TEMPERATURE);
        record.put("max_tokens", MAX_TOKENS);
        String replayFrom = System.getenv("TELME_PROBE_REPLAY_FROM");
        record.put("retrieval_mode", replayFrom == null || replayFrom.isBlank() ? "live" : "replay");
        record.put("provider_options", "bedrock".equals(GENERATOR)
                ? Map.of("reasoning_effort", "low")
                : Map.of());
        return record;
    }

    Map<String, List<FaqSearchResponse>> loadReplaySources(
            Path path, List<EvalCase> cases) throws IOException {
        JsonNode root = mapper.readTree(path.toFile());
        if (root == null || !root.isArray()) {
            throw new IllegalArgumentException("Replay 입력은 문항 배열이어야 합니다.");
        }
        Map<String, List<FaqSearchResponse>> byEvalId = new LinkedHashMap<>();
        Map<String, String> expectedQuestions = new LinkedHashMap<>();
        for (EvalCase c : cases) {
            expectedQuestions.put(c.evalId, c.question);
        }
        for (JsonNode record : root) {
            if (!record.isObject() || !record.path("sources").isArray()) {
                throw new IllegalArgumentException("Replay 입력에는 sources 배열이 필요합니다: "
                        + record.path("eval_id").asText());
            }
            List<FaqSearchResponse> sources = new ArrayList<>();
            for (JsonNode source : record.path("sources")) {
                if (!source.isObject()
                        || !isTextOrNull(source.get("question"))
                        || !isTextOrNull(source.get("answer"))) {
                    throw new IllegalArgumentException("Replay 입력의 FAQ에는 question/answer 필드가 필요합니다: "
                            + record.path("eval_id").asText());
                }
                sources.add(new FaqSearchResponse(
                        nullableLong(source, "faqId"),
                        nullableText(source, "slotId"),
                        nullableText(source, "category"),
                        nullableText(source, "question"),
                        nullableText(source, "answer"),
                        source.path("score").asDouble(0),
                        nullableInt(source, "version"),
                        source.hasNonNull("updatedAt")
                                ? java.time.LocalDate.parse(source.get("updatedAt").asText()) : null,
                        nullableInt(source, "searchRank"),
                        nullableText(source, "matchedVariant")));
            }
            String evalId = record.path("eval_id").asText();
            if (evalId.isBlank() || byEvalId.putIfAbsent(evalId, List.copyOf(sources)) != null) {
                throw new IllegalArgumentException("Replay 입력의 eval_id가 비었거나 중복입니다: " + evalId);
            }
            String expectedQuestion = expectedQuestions.get(evalId);
            if (expectedQuestion == null || !expectedQuestion.equals(record.path("question").asText())) {
                throw new IllegalArgumentException("Replay 입력의 eval_id/question이 평가셋과 다릅니다: " + evalId);
            }
        }
        if (!byEvalId.keySet().equals(expectedQuestions.keySet())) {
            throw new IllegalArgumentException("Replay 입력과 평가셋의 eval_id 구성이 다릅니다. expected="
                    + expectedQuestions.keySet().size() + ", actual=" + byEvalId.keySet().size());
        }
        return byEvalId;
    }

    private boolean isTextOrNull(JsonNode value) {
        return value != null && (value.isTextual() || value.isNull());
    }

    private Long nullableLong(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asLong() : null;
    }

    private Integer nullableInt(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asInt() : null;
    }

    private String nullableText(JsonNode n, String field) {
        return n.hasNonNull(field) ? n.get(field).asText() : null;
    }

    static Map<String, Object> bedrockRequestBody(LlmRequest request) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            requestBody.put("system", List.of(Map.of("text", request.systemPrompt())));
        }
        requestBody.put("messages", List.of(Map.of("role", "user",
                "content", List.of(Map.of("text", request.userPrompt())))));
        requestBody.put("inferenceConfig", Map.of("maxTokens", MAX_TOKENS, "temperature", TEMPERATURE));
        requestBody.put("additionalModelRequestFields", Map.of("reasoning_effort", "low"));
        return requestBody;
    }

    static void validatePaidCallOptIn(String generator, boolean allowPaid) {
        if ("bedrock".equals(generator) && !allowPaid) {
            throw new IllegalStateException(
                    "Bedrock 유료 호출은 TELME_PROBE_ALLOW_PAID=true를 명시하기 전에는 실행하지 않습니다.");
        }
    }

    static String resolveGeneratorModel(String generator, Map<String, String> environment) {
        return switch (generator) {
            case "bedrock" -> environment.getOrDefault("TELME_PROBE_MODEL", "openai.gpt-oss-120b-1:0");
            case "ollama" -> environment.getOrDefault("LLM_MODEL", "exaone3.5:7.8b");
            default -> throw new IllegalArgumentException("지원하지 않는 측정 생성기입니다: " + generator);
        };
    }

    static boolean isBedrockTargetTask(LlmRequest request) {
        return request.taskType() == TaskType.RAG_ANSWER;
    }

    private static String failureStatus(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof AnswerGuardException) {
                return "BLOCKED";
            }
        }
        return "GENERATION_ERROR";
    }

    private String reason(Throwable e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    // 측정 전용. 스트리밍 없이 한 번에 받아 토큰 하나로 흘린다
    private static final class BedrockLlmClient implements LlmClient {

        private static final String MODEL = GENERATOR_MODEL;
        private static final AtomicLong INPUT_TOKENS = new AtomicLong();
        private static final AtomicLong OUTPUT_TOKENS = new AtomicLong();
        private final HttpClient http = HttpClient.newHttpClient();
        private final ObjectMapper mapper = new ObjectMapper();
        private final LlmClient fallback;

        private BedrockLlmClient(LlmClient fallback) {
            this.fallback = fallback;
        }

        @Override
        public String generate(LlmRequest request) {
            if (!isBedrockTargetTask(request)) {
                return fallback.generate(request);
            }
            return call(request);
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            if (!isBedrockTargetTask(request)) {
                fallback.stream(request, handler);
                return;
            }
            handler.onToken(call(request));
            handler.onComplete();
        }

        private String call(LlmRequest request) {
            String body;
            try {
                body = mapper.writeValueAsString(bedrockRequestBody(request));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }

            String apiKey = System.getenv("BEDROCK_API_KEY");
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("BEDROCK_API_KEY가 없어 Bedrock 호출을 시작하지 않았습니다.");
            }
            String url = "https://bedrock-runtime."
                    + System.getenv().getOrDefault("AWS_REGION", "us-east-1")
                    + ".amazonaws.com/model/" + URLEncoder.encode(MODEL, StandardCharsets.UTF_8)
                    + "/converse";
            try {
                HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofMinutes(2))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Bedrock " + response.statusCode() + " " + response.body());
                }
                JsonNode responseBody = mapper.readTree(response.body());
                INPUT_TOKENS.addAndGet(responseBody.path("usage").path("inputTokens").asLong(0));
                OUTPUT_TOKENS.addAndGet(responseBody.path("usage").path("outputTokens").asLong(0));
                StringBuilder text = new StringBuilder();
                for (JsonNode block : responseBody
                        .path("output").path("message").path("content")) {
                    if (block.has("text")) {
                        text.append(block.get("text").asText());
                    }
                }
                return text.toString().strip();
            } catch (IOException | InterruptedException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    record EvalCase(String evalId, String type, String question, String risk) {
    }

    private record Outcome(String status, List<FaqSearchResponse> found, String answer, String error) {
    }

    // 토큰은 버린다. 기준선은 최종 답변만 본다
    private static final class SilentHandler implements LlmStreamHandler {
        @Override
        public void onToken(String token) {
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onError(Throwable error) {
        }
    }

    /**
     * 답변 품질을 같은 조건에서 비교할 수 있도록 측정값을 temperature 0으로 고정한다.
     * 이전 운영값 0.2의 영향을 재현할 때는 TELME_PROBE_TEMP로 덮어쓴다.
     */
    @TestConfiguration
    static class ZeroTemperatureConfig {

        @Bean
        BeanPostProcessor zeroTemperature() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) throws BeansException {
                    if (!"ollamaLlmClient".equals(name) || !(bean instanceof LlmClient delegate)) {
                        return bean;
                    }
                    // 남은 환각이 파이프라인 탓인지 모델 탓인지 가르기 위해 생성만 외부 모델로 바꾼다
                    if ("bedrock".equals(System.getenv("TELME_PROBE_LLM"))) {
                        return new BedrockLlmClient(delegate);
                    }
                    return new LlmClient() {
                        @Override
                        public String generate(LlmRequest request) {
                            return delegate.generate(fixed(request));
                        }

                        @Override
                        public void stream(LlmRequest request, LlmStreamHandler handler) {
                            delegate.stream(fixed(request), handler);
                        }
                    };
                }
            };
        }

        private static LlmRequest fixed(LlmRequest r) {
            return LlmRequest.builder()
                    .executionId(r.executionId())
                    .taskType(r.taskType())
                    .systemPrompt(r.systemPrompt())
                    .userPrompt(r.userPrompt())
                    .temperature(TEMPERATURE)
                    .maxTokens(r.maxTokens() == null ? MAX_TOKENS : r.maxTokens())
                    .format(r.format())
                    .contextCount(r.contextCount())
                    .promptVersion(r.promptVersion())
                    .build();
        }
    }

}
