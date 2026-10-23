package com.telme.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.config.PlanQuestionProperties;
import com.telme.consult.service.PlanCatalogAnswerer;
import com.telme.consult.service.PlanQuestion;
import com.telme.consult.service.PlanQuestionClassifier;
import com.telme.consult.service.PlanQuestionPromptTemplates;
import com.telme.consult.service.PlanQuestionType;
import com.telme.llm.config.BedrockConfig;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.plan.repository.PlanRepository;
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
 * TELME-227 요금제 목록 답변 비교. 질문을 앱의 분류기(gpt-oss)에 넣고, 요금제 표 질문이면 PlanAnswerProvider처럼
 * 분류 결과(mention)로 PlanCatalogAnswerer 답을 만든다. 회차(PLAN_CATALOG_RUNS)를 동시에 돌린다.
 * 실행: PLAN_CATALOG_PROBE=true PLAN_CATALOG_INPUT=<[{id, question}] json> PLAN_CATALOG_OUT=<결과 jsonl> LLM_PROVIDER=ollama
 *       LLM_BEDROCK_ENABLED=true AWS_PROFILE=... ./gradlew test --tests '*PlanCatalogReplayProbe'
 */
@SpringBootTest(properties = "llm.provider=ollama")
@EnabledIfEnvironmentVariable(named = "PLAN_CATALOG_PROBE", matches = "true")
class PlanCatalogReplayProbe {

    @Autowired @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) LlmClient bedrock;
    @Autowired PlanRepository planRepository;
    @Autowired PlanCatalogAnswerer catalog;

    @Test
    void 분류_결과로_요금제_목록_답을_만든다() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode inputs = mapper.readTree(new File(System.getenv("PLAN_CATALOG_INPUT")));
        Path out = Path.of(System.getenv("PLAN_CATALOG_OUT"));
        int runs = Integer.parseInt(System.getenv().getOrDefault("PLAN_CATALOG_RUNS", "3"));
        ExecutorService pool = Executors.newFixedThreadPool(runs);
        List<Future<List<String>>> futures = new ArrayList<>();
        for (int run = 1; run <= runs; run++) {
            int current = run;
            futures.add(pool.submit(() -> replay(mapper, inputs, current)));
        }
        List<String> lines = new ArrayList<>();
        for (Future<List<String>> future : futures) {
            lines.addAll(future.get());
        }
        pool.shutdown();
        Files.write(out, lines);
        System.out.println("PLAN_CATALOG|" + PlanQuestionPromptTemplates.PROMPT_VERSION + "|" + lines.size() + " rows");
    }

    private List<String> replay(ObjectMapper mapper, JsonNode inputs, int run) throws Exception {
        Recording recording = new Recording(bedrock);
        PlanQuestionClassifier classifier =
                new PlanQuestionClassifier(recording, mapper, new PlanQuestionProperties(true, true, false), planRepository);
        List<String> lines = new ArrayList<>();
        for (JsonNode input : inputs) {
            recording.reset();
            String question = input.path("question").asText();
            PlanQuestion classified = classifier.classifyQuestion(null, question);
            String answer = null;
            if (classified.type() == PlanQuestionType.PLAN_CATALOG) {
                answer = classified.mention() == null ? catalog.answer(question) : catalog.answer(question, classified.mention());
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("run", run);
            row.put("id", input.path("id").asText());
            row.put("question", question);
            row.put("prompt", PlanQuestionPromptTemplates.PROMPT_VERSION);
            row.put("type", classified.type().name());
            row.put("mention", classified.mention() == null ? null : mapper.valueToTree(classified.mention()));
            row.put("answer", answer);
            row.put("called", recording.calls > 0);
            row.put("raw", recording.raw);
            row.put("error", recording.error);
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
