package com.telme.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.rag.service.AnswerGenerator;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.dto.res.AnswerResult;
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
 * TELME-54 기준선 측정. 실제 모델·실제 검색·실제 DB로 평가 질문 150건을 돌려 답변을 파일로 남긴다.
 * 채점은 하지 않는다. 코드 수정 전후를 같은 조건에서 비교하기 위한 기록이다.
 *
 * 실행
 *   TELME_PROBE=true ./gradlew test --tests '*AnswerQualityBaselineProbe*'
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
    // 기본은 0. 서비스 기본값(0.2)의 영향을 보려면 TELME_PROBE_TEMP로 바꾼다
    private static final double TEMPERATURE =
            Double.parseDouble(System.getenv().getOrDefault("TELME_PROBE_TEMP", "0"));

    @Autowired
    private FaqSearchService faqSearchService;

    @Autowired
    private AnswerGenerator answerGenerator;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 기준선을_측정한다() throws IOException {
        List<EvalCase> cases = loadCases();
        StringBuilder out = new StringBuilder();
        Map<String, Integer> tally = new LinkedHashMap<>();
        List<Map<String, Object>> records = new ArrayList<>();

        for (EvalCase c : cases) {
            Outcome outcome = run(c);
            tally.merge(outcome.status, 1, Integer::sum);
            append(out, c, outcome);
            records.add(toRecord(c, outcome));
        }

        out.append(summary(cases.size(), tally));
        Path path = write(out.toString(), "txt");
        // 채점 스크립트가 읽는다. 사람이 볼 txt와 내용은 같다
        Path json = write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(records), "json");
        System.out.println("[Probe] 기준선 저장: " + path.toAbsolutePath());
        System.out.println("[Probe] 채점용 저장: " + json.toAbsolutePath());
        System.out.println("[Probe] " + tally);
    }

    private Outcome run(EvalCase c) {
        List<FaqSearchResponse> found;
        try {
            found = faqSearchService.search(new FaqSearchRequest(c.question, TOP_K));
        } catch (RuntimeException e) {
            return new Outcome("SEARCH_ERROR", List.of(), "", reason(e));
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
            return new Outcome("BLOCKED", found, "", reason(e));
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

    private String summary(int total, Map<String, Integer> tally) {
        StringBuilder s = new StringBuilder();
        s.append("=== 기준선 (temperature 0, topK ").append(TOP_K).append(") ===\n");
        s.append("전체 ").append(total).append("건\n");
        tally.forEach((k, v) -> s.append(String.format("  %-14s %d%n", k, v)));
        return s.toString();
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

    private Path write(String content, String extension) throws IOException {
        String dir = System.getenv().getOrDefault("TELME_PROBE_OUT", ".measure");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        Path path = Path.of(dir, "baseline-" + stamp + "." + extension);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    // 채점 스크립트가 쓰는 형태. 근거는 질문과 답변을 합쳐 한 덩어리로 넘긴다
    private Map<String, Object> toRecord(EvalCase c, Outcome o) {
        List<Map<String, Object>> sources = new ArrayList<>();
        for (FaqSearchResponse f : o.found) {
            sources.add(Map.of(
                    "faqId", f.faqId(),
                    "score", f.score(),
                    "question", f.question(),
                    "answer", f.answer()));
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
        return record;
    }

    private String reason(Throwable e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    // 측정 전용. 스트리밍 없이 한 번에 받아 토큰 하나로 흘린다
    private static final class BedrockLlmClient implements LlmClient {

        private static final String MODEL = "openai.gpt-oss-120b-1:0";
        private final HttpClient http = HttpClient.newHttpClient();
        private final ObjectMapper mapper = new ObjectMapper();

        @Override
        public String generate(LlmRequest request) {
            return call(request);
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            handler.onToken(call(request));
            handler.onComplete();
        }

        private String call(LlmRequest request) {
            String prompt = (request.systemPrompt() == null ? "" : request.systemPrompt() + "\n\n")
                    + request.userPrompt();
            String body;
            try {
                body = mapper.writeValueAsString(Map.of(
                        "messages", List.of(Map.of("role", "user",
                                "content", List.of(Map.of("text", prompt)))),
                        "inferenceConfig", Map.of("maxTokens", 2048, "temperature", 0),
                        "additionalModelRequestFields", Map.of("reasoning_effort", "low")));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }

            String url = "https://bedrock-runtime."
                    + System.getenv().getOrDefault("AWS_REGION", "us-east-1")
                    + ".amazonaws.com/model/" + URLEncoder.encode(MODEL, StandardCharsets.UTF_8)
                    + "/converse";
            try {
                HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url))
                        .header("Authorization", "Bearer " + System.getenv("BEDROCK_API_KEY"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofMinutes(2))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Bedrock " + response.statusCode() + " " + response.body());
                }
                StringBuilder text = new StringBuilder();
                for (JsonNode block : mapper.readTree(response.body())
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

    private record EvalCase(String evalId, String type, String question, String risk) {
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
     * 운영 기본값은 temperature 0.2라 같은 질문에도 답변이 매번 달라진다.
     * 전후 비교가 불가능해 측정 동안만 0으로 고정한다. 운영 설정은 건드리지 않는다.
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
                        return new BedrockLlmClient();
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
                    .maxTokens(r.maxTokens())
                    .format(r.format())
                    .contextCount(r.contextCount())
                    .promptVersion(r.promptVersion())
                    .build();
        }
    }
}
