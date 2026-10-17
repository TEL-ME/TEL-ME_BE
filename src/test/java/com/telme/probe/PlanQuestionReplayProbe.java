package com.telme.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.config.PlanQuestionProperties;
import com.telme.consult.service.PlanQuestionClassifier;
import com.telme.consult.service.PlanQuestionPromptTemplates;
import com.telme.consult.service.PlanQuestionType;
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
 * TELME-182 요금제 질문 분류 비교. 결정된 앞단으로 캡처한 실제 분류 입력(planQuestionClassifications)을
 * 앱의 분류기에 그대로 넣고 엑사원 1회, gpt-oss 3회를 동시에 돌린다. 분류기의 보정 규칙과 규칙 대체도 그대로 탄다.
 * gpt-oss는 엑사원 대체 없이 Bedrock만 불러, 호출 실패는 규칙 대체로 기록된다.
 * 실행: PLAN_REPLAY_PROBE=true PLAN_REPLAY_INPUT=<입력 json> PLAN_REPLAY_OUT=<결과 jsonl> LLM_PROVIDER=ollama
 *       LLM_BEDROCK_ENABLED=true AWS_PROFILE=... ./gradlew test --tests '*PlanQuestionReplayProbe'
 */
@SpringBootTest(properties = "llm.provider=ollama")
@EnabledIfEnvironmentVariable(named = "PLAN_REPLAY_PROBE", matches = "true")
class PlanQuestionReplayProbe {

    @Autowired LlmClient exaone;
    @Autowired @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) LlmClient bedrock;

    @Test
    void 같은_입력으로_두_모델을_비교한다() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode inputs = mapper.readTree(new File(System.getenv("PLAN_REPLAY_INPUT")));
        Path out = Path.of(System.getenv("PLAN_REPLAY_OUT"));
        List<Map<String, Object>> runs = List.of(
                Map.of("model", "exaone", "run", 1), Map.of("model", "gptoss", "run", 1),
                Map.of("model", "gptoss", "run", 2), Map.of("model", "gptoss", "run", 3));
        ExecutorService pool = Executors.newFixedThreadPool(runs.size());
        List<Future<List<String>>> futures = new ArrayList<>();
        for (Map<String, Object> run : runs) {
            LlmClient client = "exaone".equals(run.get("model")) ? exaone : bedrock;
            futures.add(pool.submit(() -> replay(mapper, inputs, client, run)));
        }
        List<String> lines = new ArrayList<>();
        for (Future<List<String>> future : futures) {
            lines.addAll(future.get());
        }
        pool.shutdown();
        Files.write(out, lines);
        System.out.println("PLAN_REPLAY|" + PlanQuestionPromptTemplates.PROMPT_VERSION + "|" + lines.size() + " rows");
    }

    private static List<String> replay(ObjectMapper mapper, JsonNode inputs, LlmClient client, Map<String, Object> run)
            throws Exception {
        Recording recording = new Recording(client);
        PlanQuestionClassifier classifier =
                new PlanQuestionClassifier(recording, mapper, new PlanQuestionProperties(true, true, false));
        List<String> lines = new ArrayList<>();
        for (JsonNode input : inputs) {
            recording.reset();
            long startedAt = System.currentTimeMillis();
            PlanQuestionType type = classifier.classify(null, input.path("question").asText());
            Map<String, Object> row = new LinkedHashMap<>(run);
            row.put("id", input.path("id").asText());
            row.put("question", input.path("question").asText());
            row.put("type", type.name());
            row.put("called", recording.calls > 0);
            row.put("raw", recording.raw);
            row.put("error", recording.error);
            row.put("ms", System.currentTimeMillis() - startedAt);
            lines.add(mapper.writeValueAsString(row));
        }
        return lines;
    }

    private static final class Recording implements LlmClient {
        private final LlmClient delegate;
        String raw;
        String error;
        int calls;

        Recording(LlmClient delegate) {
            this.delegate = delegate;
        }

        void reset() {
            raw = null;
            error = null;
            calls = 0;
        }

        @Override
        public String generate(LlmRequest request) {
            calls++;
            try {
                raw = delegate.generate(request);
                return raw;
            } catch (RuntimeException exception) {
                error = exception.getClass().getSimpleName() + ": " + exception.getMessage();
                throw exception;
            }
        }

        @Override
        public void stream(LlmRequest request, LlmStreamHandler handler) {
            delegate.stream(request, handler);
        }
    }
}
