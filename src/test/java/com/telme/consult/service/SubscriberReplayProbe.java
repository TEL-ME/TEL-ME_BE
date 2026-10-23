package com.telme.consult.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.config.PlanQuestionProperties;
import com.telme.llm.config.BedrockConfig;
import com.telme.llm.service.LlmClient;
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
 * TELME-227 추천 질문의 쓸 사람·나이대·사용량 비교. 규칙(StatedRecommendationAnswers)이 읽은 값과 분류 모델(gpt-oss)이 읽은 값을
 * 함께 남긴다. 앱(PlanAnswerProvider)처럼 규칙이 나이를 읽었어도 모델에 묻는다. 회차(SUBSCRIBER_RUNS)를 동시에 돌린다.
 * 실행: SUBSCRIBER_PROBE=true SUBSCRIBER_INPUT=<[{id, question}] json> SUBSCRIBER_OUT=<결과 jsonl> LLM_PROVIDER=ollama
 *       LLM_BEDROCK_ENABLED=true AWS_PROFILE=... ./gradlew test --tests '*SubscriberReplayProbe'
 */
@SpringBootTest(properties = "llm.provider=ollama")
@EnabledIfEnvironmentVariable(named = "SUBSCRIBER_PROBE", matches = "true")
class SubscriberReplayProbe {

    @Autowired @Qualifier(BedrockConfig.JUDGE_LLM_CLIENT) LlmClient bedrock;
    @Autowired PlanRepository planRepository;

    @Test
    void 규칙과_모델로_쓸_사람과_나이대_사용량을_읽는다() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode inputs = mapper.readTree(new File(System.getenv("SUBSCRIBER_INPUT")));
        int runs = Integer.parseInt(System.getenv().getOrDefault("SUBSCRIBER_RUNS", "3"));
        PlanQuestionClassifier classifier =
                new PlanQuestionClassifier(bedrock, mapper, new PlanQuestionProperties(true, true, false), planRepository);
        ExecutorService pool = Executors.newFixedThreadPool(runs);
        List<Future<List<String>>> futures = new ArrayList<>();
        for (int run = 1; run <= runs; run++) {
            int current = run;
            futures.add(pool.submit(() -> {
                List<String> lines = new ArrayList<>();
                for (JsonNode input : inputs) {
                    String question = input.path("question").asText();
                    Map<String, String> stated = StatedRecommendationAnswers.of(question);
                    String rule = stated.get(MemberRecommendationAnswerer.AGE_KEY);
                    String ruleData = stated.get(MemberRecommendationAnswerer.DATA_KEY);
                    long startedAt = System.currentTimeMillis();
                    PlanQuestionClassifier.Subscriber model = classifier.subscriber(null, question);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("run", current);
                    row.put("id", input.path("id").asText());
                    row.put("question", question);
                    row.put("rule", rule);
                    row.put("ruleData", ruleData == null ? null : DataUsageReplyParser.parse(ruleData).dataMb() / 1024.0);
                    row.put("model", model == null || model.group() == null ? null : model.group().name());
                    row.put("modelUser", model == null ? null : model.other() ? "OTHER" : "SELF");
                    row.put("modelData", model == null ? null : model.monthlyDataGb());
                    row.put("modelMs", System.currentTimeMillis() - startedAt);
                    lines.add(mapper.writeValueAsString(row));
                }
                return lines;
            }));
        }
        List<String> lines = new ArrayList<>();
        for (Future<List<String>> future : futures) {
            lines.addAll(future.get());
        }
        pool.shutdown();
        Files.write(Path.of(System.getenv("SUBSCRIBER_OUT")), lines);
        System.out.println("SUBSCRIBER|" + PlanQuestionPromptTemplates.SUBSCRIBER_AGE_PROMPT_VERSION + "+" + PlanQuestionPromptTemplates.SUBSCRIBER_USAGE_PROMPT_VERSION + "|" + lines.size() + " rows");
    }
}
