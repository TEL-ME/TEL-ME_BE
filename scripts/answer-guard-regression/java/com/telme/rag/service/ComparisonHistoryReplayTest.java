package com.telme.rag.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.rag.exception.AnswerGuardException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComparisonHistoryReplayTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AnswerGuard guard = new AnswerGuard();
    @Test
    void replayPreviouslyRecordedModelOutputs() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, JsonNode> baseline = new LinkedHashMap<>();
        try (var resource = getClass().getResourceAsStream("/answer-guard-recovery/history-baseline.json")) {
            assertThat(resource).isNotNull();
            for (JsonNode row : mapper.readTree(resource)) {
                baseline.put(row.path("id").asText(), row);
            }
        }
        try (var resource = getClass().getResourceAsStream("/answer-guard-recovery/history.json")) {
            assertThat(resource).isNotNull();
            for (JsonNode test : mapper.readTree(resource)) {
                String output;
                String exception = "";
                try {
                    output = guard.applyEvidencePolicy(test.path("answer").asText(),
                            test.path("evidence").asText(), test.path("query").asText(),
                            test.path("confirmedValues").asText());
                } catch (AnswerGuardException rejected) {
                    output = AnswerPromptTemplates.NO_EVIDENCE_ANSWER;
                    exception = rejected.getMessage();
                }
                String id = test.path("id").asText();
                assertThat(baseline).containsKey(id);
                assertThat(output).as("historical output: %s", id)
                        .isEqualTo(baseline.get(id).path("output").asText());
                assertThat(exception).as("historical rejection: %s", id)
                        .isEqualTo(baseline.get(id).path("exception").asText());
                rows.add(Map.of("id", id, "output", output, "exception", exception));
            }
        }
        Path report = Path.of(System.getProperty("recoveryReport", "build/reports/answer-guard-recovery/replay.json"));
        Files.createDirectories(report.getParent());
        String name = report.getFileName().toString().replace(".json", "-history.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(report.resolveSibling(name).toFile(), rows);
        assertThat(rows).hasSize(651);
    }

}
