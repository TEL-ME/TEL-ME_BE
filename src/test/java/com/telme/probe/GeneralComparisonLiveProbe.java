package com.telme.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

// 실제 HTTP 채팅 흐름을 실행하고 비교 판정의 입력과 원문 출력을 함께 보관한다.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
        "server.port=18089", "llm.provider=ollama", "llm.model=exaone3.5:7.8b",
        "chat.execution.timeout-scheduler-enabled=false"
})
@EnabledIfEnvironmentVariable(named = "TELME_GENERAL_COMPARISON_PROBE", matches = "true")
class GeneralComparisonLiveProbe {
    @Autowired ObjectMapper mapper;
    @MockitoSpyBean(name = "baseLlmClient") LlmClient model;

    @Test
    void runActualHttpScenariosAndRecordModelOutputs() throws Exception {
        List<Map<String, Object>> calls = Collections.synchronizedList(new ArrayList<>());
        doAnswer(invocation -> {
            LlmRequest request = invocation.getArgument(0);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("startedAt", Instant.now().toString());
            row.put("request", request);
            try {
                Object response = invocation.callRealMethod();
                row.put("rawOutput", response);
                return response;
            } catch (Throwable failure) {
                row.put("error", failure.getClass().getSimpleName() + ": " + failure.getMessage());
                throw failure;
            } finally {
                row.put("finishedAt", Instant.now().toString());
                calls.add(row);
            }
        }).when(model).generate(any());
        String database = System.getenv("POSTGRES_DB");
        assertThat(database).startsWith("telme_compound_pr_review");
        Path output = Path.of(System.getenv().getOrDefault("TELME_GENERAL_COMPARISON_OUT",
                "scripts/compound-faq-evaluation/runs/general-comparison-current.json"));
        List<String> command = List.of("python", "scripts/compound-faq-evaluation/run_live_api.py",
                "--database", database, "--postgres-container",
                System.getenv().getOrDefault("TELME_PROBE_POSTGRES_CONTAINER", "telme-postgres"),
                "--scenario", "general-comparison", "--repetitions",
                System.getenv().getOrDefault("TELME_GENERAL_COMPARISON_REPETITIONS", "10"),
                "--out", output.toString());
        int exitCode;
        try {
            ProcessBuilder process = new ProcessBuilder(command).inheritIO();
            process.environment().put("PYTHONIOENCODING", "utf-8");
            exitCode = process.start().waitFor();
        } finally {
            Path modelOutput = output.resolveSibling(output.getFileName() + ".model-calls.json");
            Files.createDirectories(modelOutput.getParent());
            List<Map<String, Object>> snapshot;
            synchronized (calls) {
                snapshot = List.copyOf(calls);
            }
            Files.writeString(modelOutput, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(snapshot));
        }
        assertThat(exitCode).isZero();
    }
}
