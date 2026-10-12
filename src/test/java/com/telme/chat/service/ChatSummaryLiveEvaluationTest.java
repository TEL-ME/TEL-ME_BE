package com.telme.chat.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.config.ChatSummaryProperties;
import com.telme.chat.converter.ChatSummaryConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import com.telme.llm.service.LlmStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// Python은 입력만 만든다. 세 비교안 모두 Spring에 등록된 동일 LLM 클라이언트를 호출한다.
@EnabledIfEnvironmentVariable(named = "RUN_TELME121_LIVE", matches = "true")
@SpringBootTest(properties = {"llm.provider=ollama", "chat.summary.grounded-output=false"})
class ChatSummaryLiveEvaluationTest {
    @Autowired LlmClient client;
    @Autowired ObjectMapper mapper;
    @Autowired ChatSummaryConverter converter;

    @Test
    void comparesFrozenInputsAndPreservesEveryRawRequestAndOutput() throws Exception {
        Path root = Path.of("scripts", "multiturn-evaluation");
        JsonNode fixtures = mapper.readTree(root.resolve("summary-cases.json").toFile());
        String baseline = mapper.readTree(root.resolve("baseline-summary-prompt.json").toFile())
                .path("systemPrompt").asText();
        Path directory = Path.of(".measure", "telme121", "summary-comparison");
        Files.createDirectories(directory);
        Path output = directory.resolve("raw-" + System.currentTimeMillis() + ".jsonl");
        int completed = 0;
        int limit = Integer.parseInt(System.getenv().getOrDefault("TELME121_CASE_LIMIT", "60"));
        for (JsonNode fixture : fixtures.path("cases")) {
            if (completed >= limit) break;
            List<ChatContextMessage> messages = new ArrayList<>();
            for (JsonNode message : fixture.path("messages")) {
                messages.add(mapper.treeToValue(message, ChatContextMessage.class));
            }
            for (String mode : List.of("BASELINE_TEXT", "IMPROVED_TEXT", "GROUNDED_MEMORY")) {
                long start = System.nanoTime();
                var row = new LinkedHashMap<String, Object>();
                row.put("case", fixture);
                row.put("mode", mode);
                row.put("startedAt", Instant.now().toString());
                row.put("model", System.getenv().getOrDefault("LLM_MODEL", "exaone3.5:7.8b"));
                row.put("temperature", 0.0);
                row.put("contextSize", 8192);
                row.put("baselineCommit", "3516b24");
                CapturingClient observed = new CapturingClient(client);
                try {
                    String stored = null;
                    List<List<ChatContextMessage>> batches = List.of(messages);
                    if (fixture.path("id").asText().endsWith("-06")) {
                        batches = List.of(messages.subList(0, 4), messages.subList(4, 6), List.of(
                                new ChatContextMessage(7L, 7, com.telme.chat.entity.ChatMessage.Role.USER,
                                        com.telme.chat.entity.ChatMessage.MessageType.QUESTION, "감사합니다.", null),
                                new ChatContextMessage(8L, 8, com.telme.chat.entity.ChatMessage.Role.ASSISTANT,
                                        com.telme.chat.entity.ChatMessage.MessageType.ANSWER, "천만에요.", null)));
                    }
                    var stages = new ArrayList<Map<String, Object>>();
                    for (List<ChatContextMessage> batch : batches) {
                        stored = generate(mode, baseline, stored, batch, observed, row);
                        if (stored == null) throw new IllegalArgumentException("요약 출력이 비어 있습니다.");
                        stages.add(Map.of("throughSequenceNo", batch.getLast().sequenceNo(),
                                "storedSummary", stored, "inputMessages", batch));
                    }
                    row.put("stages", stages);
                    String rendered = converter.render(stored);
                    row.put("renderedSummary", rendered);
                    row.put("validation", "ACCEPTED");
                    row.put("estimatedTokens", new ChatTokenEstimator().estimatePromptPart(rendered));
                    var checks = new LinkedHashMap<String, Boolean>();
                    for (JsonNode anchor : fixture.path("requiredAnchors")) {
                        checks.put(anchor.asText(), rendered != null && rendered.contains(anchor.asText()));
                    }
                    row.put("literalAnchorChecks", checks);
                    row.put("selectedMessageIds", converter.sources(stored).stream().map(ChatContextMessage::messageId).toList());
                } catch (RuntimeException failure) {
                    row.put("validation", "REJECTED");
                    row.put("error", failure.getMessage());
                }
                row.put("requests", observed.requests);
                row.put("rawResponses", observed.responses);
                row.put("elapsedMillis", (System.nanoTime() - start) / 1_000_000);
                Files.writeString(output, mapper.writeValueAsString(row) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            completed++;
            System.out.println("TELME121 summary completed " + completed + "/" + limit + " " + fixture.path("id").asText());
        }
        assertThat(completed).isEqualTo(limit);
    }

    private String generate(String mode, String baseline, String previous, List<ChatContextMessage> messages,
            CapturingClient observed, Map<String, Object> row) {
        if (mode.equals("BASELINE_TEXT")) {
            var request = LlmRequest.builder().taskType(TaskType.SUMMARY)
                    .systemPrompt(baseline).userPrompt(ChatSummaryPrompt.buildUserPrompt(previous, messages))
                    .temperature(0.0).maxTokens(512).promptVersion("summary-baseline-3516b24").build();
            return ChatSummaryNormalizer.normalize(observed.generate(request));
        }
        boolean grounded = mode.equals("GROUNDED_MEMORY");
        var settings = new ChatSummaryProperties(16, 2048, 8, 1024, 16, 3072, 512, grounded);
        ChatSummaryStore store = mock(ChatSummaryStore.class);
        var snapshot = new ChatSummarySnapshot(null, 1L, previous, 0, messages.getLast().sequenceNo(), messages);
        when(store.prepare(any())).thenReturn(Optional.of(snapshot));
        when(store.saveIfCurrent(any(), any())).thenAnswer(call -> {
            row.put("storedSummary", call.getArgument(1));
            return true;
        });
        new ChatSummaryService(store, observed, settings, converter)
                .summarizeIfNeeded(new ChatSummaryRequested(1L, 1L, messages.getLast().sequenceNo()));
        return (String) row.get("storedSummary");
    }

    private static final class CapturingClient implements LlmClient {
        private final LlmClient delegate;
        private final List<LlmRequest> requests = new ArrayList<>();
        private final List<String> responses = new ArrayList<>();

        CapturingClient(LlmClient delegate) { this.delegate = delegate; }

        public String generate(LlmRequest request) {
            requests.add(request);
            String result = delegate.generate(request);
            responses.add(result);
            return result;
        }

        public void stream(LlmRequest request, LlmStreamHandler handler) {
            delegate.stream(request, handler);
        }
    }
}
