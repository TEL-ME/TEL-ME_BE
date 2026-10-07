package com.telme.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.converter.AnswerContextConverter;
import com.telme.rag.service.EvidenceRelevanceChecker;
import com.telme.rag.service.EvidenceRelevancePromptTemplates;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

// 현행 근거 판정기(EvidenceRelevanceChecker)를 앱 코드 그대로 실제 검색, 실제 모델로 돌려 판정 정확도의 기준선 확인
// 라벨은 scripts/data/evidence_check_cases_25.json에 측정 전 고정
@SpringBootTest(properties = {
        "llm.provider=ollama",
        "rag.evidence-check.enabled=true",
        "faq.search-test-api-enabled=false"
})
@Import(EvidenceCheckProbe.CapturingConfig.class)
@EnabledIfEnvironmentVariable(named = "TELME_PROBE", matches = "true")
class EvidenceCheckProbe {

    private static final Path CASES = Path.of("scripts/data/evidence_check_cases_25.json");
    // 채팅 FAQ 경로(FaqSearchAnswerProvider)와 같은 값
    private static final int TOP_K = 3;
    private static final int REPEAT =
            Integer.parseInt(System.getenv().getOrDefault("TELME_PROBE_REPEAT", "3"));
    // BASELINE: 채팅과 같은 근거(Q+A) / A: FAQ 질문을 빼고 답변 본문만
    // B: 근거는 Q+A, 묻는 항목부터 적게 하는 프롬프트 / AB: 답변 본문만 + B 프롬프트
    private static final String VARIANT = System.getenv().getOrDefault("TELME_PROBE_VARIANT", "BASELINE");
    // 판정기(EvidenceCheckProperties)와 같은 기본값
    // B는 출력이 길어져 잘리는지 rawResponse로 확인
    private static final int MAX_TOKENS = 300;

    // 개선안 B. 평가 케이스를 예시로 쓰지 않는다(예시 누출 방지). 규칙만 적는다
    static final String B_PROMPT_VERSION = "evidence-check-b1";
    static final String B_SYSTEM_PROMPT = """
        당신은 통신사 FAQ 검색 결과를 검수합니다.
        고객 질문에 답할 정보가 FAQ에 있는지 아래 순서대로 판단하십시오.

        1. asked: 고객이 알고 싶어 하는 것을 항목별로 짧게 적습니다.
           질문에 나온 행위(신청, 해지, 변경 등)와 알고 싶은 정보의 종류(비용, 소요 시간, 마감 기한,
           방법, 장소, 가능 여부, 혜택, 기준 등)를 바꾸지 말고 그대로 적습니다.
        2. found: asked의 각 항목이 FAQ에 있으면 true, 없으면 false를 같은 순서로 적습니다.
           - 행위가 다르면 없는 것입니다.
           - 정보의 종류가 다르면 없는 것입니다. 주제가 같아도 마찬가지입니다.
           - FAQ에 적힌 값으로 바로 계산하거나 읽어 낼 수 있으면 있는 것입니다.
        3. answerable: found에 true가 하나라도 있으면 true, 모두 false면 false.

        JSON만 출력하십시오.
        {"asked": ["..."], "found": [true 또는 false], "answerable": true 또는 false}
        """;

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private FaqSearchService faqSearchService;

    @Autowired
    private AnswerContextConverter contextConverter;

    @Autowired
    private EvidenceRelevanceChecker checker;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Test
    void 현행_판정기의_기준선을_측정한다() throws IOException {
        JsonNode cases = mapper.readTree(CASES.toFile()).path("cases");
        List<Map<String, Object>> records = new ArrayList<>();

        for (JsonNode c : cases) {
            String question = c.path("question").asText();
            List<FaqSearchResponse> results = faqSearchService.search(new FaqSearchRequest(question, TOP_K));
            List<Map<String, Object>> evidence = results.stream().map(r -> {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("slotId", r.slotId());
                e.put("score", r.score());
                e.put("matchedVariant", r.matchedVariant());
                e.put("question", r.question());
                e.put("answer", r.answer());
                return e;
            }).toList();

            List<Map<String, Object>> runs = new ArrayList<>();
            if (!results.isEmpty()) {
                String context = toContext(results);
                for (int i = 1; i <= REPEAT; i++) {
                    CapturingLlmClient.reset();
                    long start = System.nanoTime();
                    boolean answerable = judge(question, context);
                    long elapsedMs = (System.nanoTime() - start) / 1_000_000;
                    Map<String, Object> run = new LinkedHashMap<>();
                    run.put("run", i);
                    run.put("answerable", answerable);
                    run.put("rawResponse", CapturingLlmClient.lastResponse());
                    run.put("error", CapturingLlmClient.lastError());
                    run.put("elapsedMs", elapsedMs);
                    runs.add(run);
                }
            }

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("caseId", c.path("case_id").asText());
            record.put("group", c.path("group").asText());
            record.put("expectedAnswerable", c.path("expected_answerable").asBoolean());
            record.put("question", question);
            record.put("evidence", evidence);
            record.put("runs", runs);
            records.add(record);
            System.out.println(">>> EVIDENCE-PROBE " + record.get("caseId") + " evidence=" + evidence.size()
                    + " runs=" + runs.stream().map(r -> String.valueOf(r.get("answerable"))).toList());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("measuredAt", LocalDateTime.now().toString());
        out.put("promptVersion", usesBPrompt() ? B_PROMPT_VERSION : EvidenceRelevancePromptTemplates.PROMPT_VERSION);
        out.put("variant", VARIANT);
        out.put("labelVersion", mapper.readTree(CASES.toFile()).path("label_version").asText("v1"));
        out.put("topK", TOP_K);
        out.put("repeat", REPEAT);
        out.put("cases", records);

        Path dir = Path.of(System.getenv().getOrDefault("TELME_PROBE_OUT", ".measure"));
        Files.createDirectories(dir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path file = dir.resolve("evidence-check-" + VARIANT.toLowerCase() + "-" + stamp + ".json");
        mapper.writeValue(file.toFile(), out);
        System.out.println(">>> EVIDENCE-PROBE saved " + file.toAbsolutePath());
    }

    private String toContext(List<FaqSearchResponse> results) {
        return switch (VARIANT) {
            case "BASELINE", "B" -> contextConverter.toContext(results);
            // AnswerGuard처럼 답변 본문만 근거로 본다
            case "A", "AB" -> IntStream.range(0, results.size())
                    .mapToObj(i -> "[%d] %s".formatted(i + 1, results.get(i).answer()))
                    .collect(Collectors.joining("\n\n"));
            default -> throw new IllegalArgumentException("알 수 없는 TELME_PROBE_VARIANT: " + VARIANT);
        };
    }

    private static boolean usesBPrompt() {
        return VARIANT.equals("B") || VARIANT.equals("AB");
    }

    // BASELINE·A는 실제 판정기를, B·AB는 같은 요청 설정에 프롬프트만 바꿔 부른다
    private boolean judge(String question, String context) {
        if (!usesBPrompt()) {
            return checker.canAnswer(null, question, context);
        }
        LlmRequest request = LlmRequest.builder()
                .taskType(TaskType.ROUTING)
                .systemPrompt(B_SYSTEM_PROMPT)
                .userPrompt(EvidenceRelevancePromptTemplates.userPrompt(context, question))
                .format(ResponseFormat.JSON)
                .temperature(0.0)
                .maxTokens(MAX_TOKENS)
                .promptVersion(B_PROMPT_VERSION)
                .build();
        try {
            return parseLikeChecker(llmClient.generate(request));
        } catch (RuntimeException e) {
            // 판정기와 같은 fail-open
            return true;
        }
    }

    // EvidenceRelevanceChecker.parse와 같은 규칙
    // 비었거나 깨졌거나 필드가 없으면 true
    private boolean parseLikeChecker(String json) {
        if (json == null || json.isBlank()) {
            return true;
        }
        try {
            JsonNode node = mapper.readTree(json).path("answerable");
            return node.isMissingNode() || node.asBoolean(true);
        } catch (Exception e) {
            return true;
        }
    }

    // 판정기가 실패를 true를 돌려주므로, 실제 응답, 예외를 따로 잡아 두려고 감쌈
    @TestConfiguration
    static class CapturingConfig {
        @Bean
        static BeanPostProcessor capturingLlmClientPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    if ("baseLlmClient".equals(beanName) && bean instanceof LlmClient client) {
                        return new CapturingLlmClient(client);
                    }
                    return bean;
                }
            };
        }
    }

    static final class CapturingLlmClient implements LlmClient {
        private static final ThreadLocal<String> LAST_RESPONSE = new ThreadLocal<>();
        private static final ThreadLocal<String> LAST_ERROR = new ThreadLocal<>();
        private final LlmClient delegate;

        CapturingLlmClient(LlmClient delegate) {
            this.delegate = delegate;
        }

        static void reset() {
            LAST_RESPONSE.remove();
            LAST_ERROR.remove();
        }

        static String lastResponse() {
            return LAST_RESPONSE.get();
        }

        static String lastError() {
            return LAST_ERROR.get();
        }

        @Override
        public String generate(LlmRequest request) {
            try {
                String response = delegate.generate(request);
                LAST_RESPONSE.set(response);
                return response;
            } catch (RuntimeException e) {
                LAST_ERROR.set(e.getClass().getSimpleName() + ": " + e.getMessage());
                throw e;
            }
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            delegate.stream(request, handler);
        }
    }
}
