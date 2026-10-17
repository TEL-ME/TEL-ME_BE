package com.telme.consult.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.ConsultationQuestion;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.config.BedrockConfig;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * TELME-184 비교 근거 검증 재확인. 결정된 앞단으로 캡처한 실제 입력(comparisonEvidenceInputs의 문장과 원문 검색 결과)을
 * 앱의 비교 근거 검증에 그대로 넣고, 대상별 추가 검색은 앱 검색(FAQ_CANDIDATE와 같은 후보 10개)을 그대로 부른다.
 * 앱의 대상 확인과 인용문 원문 대조도 그대로 탄다. gpt-oss는 엑사원 대체 없이 Bedrock만 부른다.
 * 회색 구간 때문에 다시 고르는 두 번째 시도는 다시 만들지 않고 첫 시도 입력만 쓴다.
 * 모델(COMPARISON_REPLAY_MODELS, 기본 gptoss:3)은 모델:회차 수다. 엑사원은 다른 실행과 겹치지 않게 따로 돌린다.
 * 실행: COMPARISON_REPLAY_PROBE=true COMPARISON_REPLAY_INPUT=<입력 json> COMPARISON_REPLAY_OUT=<결과 jsonl>
 *       POSTGRES_PORT=5440 LLM_PROVIDER=ollama LLM_BEDROCK_ENABLED=true AWS_PROFILE=...
 *       ./gradlew test --tests '*ComparisonEvidenceReplayProbe'
 */
@SpringBootTest(properties = "llm.provider=ollama")
@EnabledIfEnvironmentVariable(named = "COMPARISON_REPLAY_PROBE", matches = "true")
class ComparisonEvidenceReplayProbe {

    private static final int CANDIDATE_TOP_K = 10;

    @Autowired LlmClient exaone;
    @Autowired @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) LlmClient bedrock;
    @Autowired ObjectMapper mapper;
    @Autowired FaqSearchService searches;

    @Test
    void 같은_입력으로_두_모델을_비교한다() throws Exception {
        JsonNode inputs = mapper.readTree(new File(System.getenv("COMPARISON_REPLAY_INPUT")));
        Path out = Path.of(System.getenv("COMPARISON_REPLAY_OUT"));
        List<Map<String, Object>> runs = new ArrayList<>();
        for (String model : System.getenv().getOrDefault("COMPARISON_REPLAY_MODELS", "gptoss:3").split(",")) {
            String[] parts = model.split(":");
            for (int run = 1; run <= Integer.parseInt(parts[1]); run++) {
                runs.add(Map.of("model", parts[0], "run", run));
            }
        }
        ExecutorService pool = Executors.newFixedThreadPool(runs.size());
        List<Future<List<String>>> futures = new ArrayList<>();
        for (Map<String, Object> run : runs) {
            LlmClient client = "exaone".equals(run.get("model")) ? exaone : bedrock;
            futures.add(pool.submit(() -> replay(inputs, client, run)));
        }
        List<String> lines = new ArrayList<>();
        for (Future<List<String>> future : futures) {
            lines.addAll(future.get());
        }
        pool.shutdown();
        Files.write(out, lines);
        System.out.println("COMPARISON_REPLAY|" + lines.size() + " rows");
    }

    private List<String> replay(JsonNode inputs, LlmClient client, Map<String, Object> run) throws Exception {
        Recording recording = new Recording(client);
        LlmComparisonEvidenceResolver resolver = new LlmComparisonEvidenceResolver(recording, mapper);
        List<String> lines = new ArrayList<>();
        for (JsonNode input : inputs) {
            List<FaqSearchResponse> original = new ArrayList<>();
            for (JsonNode source : input.path("originalSources")) {
                original.add(mapper.treeToValue(source, FaqSearchResponse.class));
            }
            List<String> searched = new ArrayList<>();
            recording.reset();
            long startedAt = System.currentTimeMillis();
            String failure = null;
            ComparisonEvidenceResolver.Resolution resolution = null;
            try {
                resolution = resolver.resolveDetailed(null, null, input.path("question").asText(), original,
                        (query, kind) -> {
                            searched.add(kind + ":" + query);
                            return searches.searchCandidates(
                                    new FaqSearchRequest(ConsultationQuestion.extract(query), CANDIDATE_TOP_K));
                        });
            } catch (RuntimeException exception) {
                failure = exception.getClass().getSimpleName() + ": " + exception.getMessage();
            }
            Map<String, Object> row = new LinkedHashMap<>(run);
            row.put("id", input.path("id").asText());
            row.put("answered", resolution != null && resolution.answer() != null);
            row.put("answer", resolution == null ? null : resolution.answer());
            row.put("sources", resolution == null ? List.of() : resolution.sources());
            row.put("searched", searched);
            row.put("calls", recording.raws.size());
            row.put("raws", recording.raws);
            row.put("errors", recording.errors);
            row.put("failure", failure);
            row.put("ms", System.currentTimeMillis() - startedAt);
            lines.add(mapper.writeValueAsString(row));
        }
        return lines;
    }

    private static final class Recording implements LlmClient {
        private final LlmClient delegate;
        List<String> raws = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        Recording(LlmClient delegate) {
            this.delegate = delegate;
        }

        void reset() {
            raws = new ArrayList<>();
            errors = new ArrayList<>();
        }

        @Override
        public String generate(LlmRequest request) {
            try {
                String raw = delegate.generate(request);
                raws.add(raw);
                return raw;
            } catch (RuntimeException exception) {
                errors.add(exception.getClass().getSimpleName() + ": " + exception.getMessage());
                throw exception;
            }
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            delegate.stream(request, handler);
        }
    }
}
