package com.telme.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.converter.ChatSummaryConverter;
import com.telme.llm.config.BedrockProperties;
import com.telme.llm.converter.BedrockRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.BedrockClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * TELME-194 리뷰: 대화 요약(JSON 선택 출력)과 세션 제목을 앱과 같은 Bedrock 경로로 불러 출력이 쓸 수 있는지 본다.
 * 호출 수는 SUMMARY_CASES + TITLE_CASES. 실행: BEDROCK_SUMMARY_TITLE_PROBE=true ./gradlew test --tests '*BedrockSummaryTitleProbe'
 */
@EnabledIfEnvironmentVariable(named = "BEDROCK_SUMMARY_TITLE_PROBE", matches = "true")
class BedrockSummaryTitleProbe {
    private static final int SUMMARY_CASES = 15;
    private static final int TITLE_CASES = 20;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void run() throws Exception {
        var properties = new BedrockProperties(true, "us-east-1", "openai.gpt-oss-120b-1:0", Duration.ofSeconds(8),
                "low", 1000, 50, Duration.ofSeconds(1));
        var runtime = BedrockRuntimeClient.builder()
                .region(Region.of(properties.region()))
                .httpClientBuilder(Apache5HttpClient.builder().maxConnections(properties.maxConnections())
                        .connectionAcquisitionTimeout(properties.connectionAcquireTimeout()))
                .overrideConfiguration(config -> config.apiCallTimeout(properties.timeout())
                        .retryStrategy(AwsRetryStrategy.doNotRetry()))
                .build();
        var client = new BedrockClient(runtime, new BedrockRequestConverter(properties));
        var converter = new ChatSummaryConverter(mapper);
        JsonNode cases = mapper.readTree(Path.of("scripts/multiturn-evaluation/summary-cases.json").toFile()).path("cases");

        List<Map<String, Object>> rows = new ArrayList<>();
        int calls = 0;
        int summaryOk = 0;
        for (int i = 0; i < cases.size() && rows.stream().filter(r -> "summary".equals(r.get("kind"))).count() < SUMMARY_CASES;
                i += 4) {
            JsonNode fixture = cases.get(i);
            List<ChatContextMessage> messages = new ArrayList<>();
            for (JsonNode message : fixture.path("messages")) {
                messages.add(mapper.treeToValue(message, ChatContextMessage.class));
            }
            var estimator = new ChatTokenEstimator();
            if (converter.candidatesFor(null, messages).isEmpty()) {
                continue;
            }
            LlmRequest request = LlmRequest.builder().taskType(TaskType.SUMMARY)
                    .systemPrompt(ChatSummaryPrompt.SELECTION_SYSTEM_PROMPT)
                    .userPrompt(ChatSummaryPrompt.buildSelectionPrompt(null, messages, 512, converter))
                    .format(ResponseFormat.JSON).promptVersion("summary-grounded-v1").temperature(0.0).maxTokens(512).build();
            var row = new LinkedHashMap<String, Object>();
            row.put("kind", "summary");
            row.put("id", fixture.path("id").asText());
            calls++;
            try {
                String generated = client.generate(request);
                String stored = converter.validateAndStore(generated, null, messages, 512, estimator);
                row.put("valid", stored != null);
                row.put("generatedHead", generated == null ? null : generated.substring(0, Math.min(120, generated.length())));
                if (stored != null) {
                    summaryOk++;
                }
            } catch (RuntimeException failure) {
                row.put("valid", false);
                row.put("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
            rows.add(row);
        }

        int titleOk = 0;
        int titles = 0;
        for (int i = 0; i < cases.size() && titles < TITLE_CASES; i += 3) {
            String question = null;
            for (JsonNode message : cases.get(i).path("messages")) {
                if ("USER".equals(message.path("role").asText())) {
                    question = message.path("content").asText();
                    break;
                }
            }
            if (question == null) {
                continue;
            }
            titles++;
            calls++;
            LlmRequest request = LlmRequest.builder().taskType(TaskType.SESSION_TITLE)
                    .systemPrompt(ChatSessionTitlePrompt.systemPrompt(30))
                    .userPrompt(ChatSessionTitlePrompt.buildUserPrompt(question))
                    .temperature(0.2).maxTokens(512).build();
            var row = new LinkedHashMap<String, Object>();
            row.put("kind", "title");
            row.put("id", cases.get(i).path("id").asText());
            row.put("question", question);
            try {
                String generated = client.generate(request);
                boolean ok = generated != null && !generated.isBlank();
                row.put("valid", ok);
                row.put("title", generated);
                if (ok) {
                    titleOk++;
                }
            } catch (RuntimeException failure) {
                row.put("valid", false);
                row.put("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
            rows.add(row);
        }
        var summary = new LinkedHashMap<String, Object>();
        summary.put("kind", "total");
        summary.put("bedrockCalls", calls);
        summary.put("summaryValid", summaryOk);
        summary.put("titleValid", titleOk);
        rows.add(summary);
        List<String> lines = new ArrayList<>();
        for (var row : rows) {
            lines.add(mapper.writeValueAsString(row));
        }
        Files.write(Path.of(".measure/telme194/summary-title-probe.jsonl"), lines);
    }
}
