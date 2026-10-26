package com.telme.probe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.service.QueryRoutingService;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.service.LlmClient;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

// 별도 평가 DB에서 실제 모델 호출과 라우팅 저장을 기록한다.
@SpringBootTest(properties = {"llm.provider=ollama", "llm.model=exaone3.5:7.8b",
        "telme.consult.chat-integration-enabled=false"})
@EnabledIfEnvironmentVariable(named = "TELME_SPACING_PROBE", matches = "true")
@Transactional
class SpacingRoutingProbe {
    @Autowired QueryRoutingService routing;
    @Autowired ChatMessageRepository messages;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ApplicationContext context;
    @MockitoSpyBean(name = "baseLlmClient") LlmClient model;

    @Test
    void recordsIndependentRequestsAndSpacingInvariance() throws Exception {
        var input = mapper.readTree(Path.of(System.getenv().getOrDefault("TELME_SPACING_CASES",
                "scripts/spacing-routing-evaluation/cases.json")).toFile());
        var cases = input.isArray() ? input : input.path(System.getenv().getOrDefault(
                "TELME_SPACING_GROUP", "base"));
        if (!cases.isArray()) {
            throw new IllegalArgumentException("평가 입력 그룹은 배열이어야 합니다.");
        }
        List<Map<String, Object>> calls = new ArrayList<>();
        doAnswer(call -> {
            LlmRequest request = call.getArgument(0);
            Map<String, Object> capture = new LinkedHashMap<>();
            calls.add(capture);
            capture.put("request", request);
            long start = System.nanoTime();
            try {
                String raw = (String) call.callRealMethod();
                capture.put("response", raw);
                return raw;
            } finally {
                capture.put("modelElapsedMs", (System.nanoTime() - start) / 1_000_000);
            }
        }).when(model).generate(any());

        // 변경 전 구현을 임시 테스트 클래스로 복사했을 때만 동일 Spring 경로로 비교한다.
        String baselineClass = System.getenv("TELME_SPACING_BASELINE_CLASS");
        Object processor = baselineClass == null ? routing
                : context.getAutowireCapableBeanFactory().createBean(Class.forName(baselineClass));
        var method = processor.getClass().getMethod("routeSingleConsult",
                com.telme.chat.entity.ChatMessage.class, com.telme.chat.service.ChatContext.class);
        long user = jdbc.queryForObject("INSERT INTO users(email,name) VALUES (?,'띄어쓰기 검증') RETURNING user_id",
                Long.class, UUID.randomUUID() + "@example.com");
        long session = jdbc.queryForObject("INSERT INTO chat_sessions(user_id) VALUES (?) RETURNING session_id",
                Long.class, user);
        var rows = new ArrayList<Map<String, Object>>();
        var failures = new ArrayList<String>();
        Map<String, List<String>> groupOutcomes = new LinkedHashMap<>();
        Path output = Path.of(System.getenv().getOrDefault("TELME_SPACING_OUT",
                ".measure/telme132/live-routing.json.gz"));
        Files.createDirectories(output.getParent());
        for (var item : cases) {
            int repetitions = item.path("repetitions").asInt(1);
            for (int attempt = 1; attempt <= repetitions; attempt++) {
                for (var variant : item.path("variants")) {
                    String question = variant.asText();
                    long message = jdbc.queryForObject("INSERT INTO chat_messages(session_id,sequence_no,role,"
                            + "message_type,content,status) VALUES (?,?,'USER','QUESTION',?,'COMPLETED')"
                            + " RETURNING message_id", Long.class, session, rows.size() + 1, question);
                    calls.clear();
                    var row = new LinkedHashMap<String, Object>();
                    row.put("id", item.path("id").asText());
                    row.put("question", question);
                    row.put("attempt", attempt);
                    row.put("expectedCount", item.path("expectedCount").asInt());
                    row.put("expectedIntent", item.path("expectedIntent").asText("FAQ"));
                    String outcome;
                    try {
                        IntentRouteResponse result = (IntentRouteResponse) method.invoke(
                                processor, messages.findById(message).orElseThrow(), null);
                        row.put("route", result);
                        outcome = result.intent() + ":" + result.subQueries().size();
                        if ((int) row.get("expectedCount") >= 0
                                && (!result.intent().name().equals(row.get("expectedIntent"))
                                || result.subQueries().size() != (int) row.get("expectedCount"))) {
                            failures.add(item.path("id").asText() + ":" + attempt + ":" + outcome);
                        }
                    } catch (InvocationTargetException failed) {
                        outcome = "REJECTED:" + failed.getCause().getClass().getSimpleName();
                        row.put("error", failed.getCause().getMessage());
                        if ((int) row.get("expectedCount") >= 0) {
                            failures.add(item.path("id").asText() + ":" + attempt + ":" + outcome);
                        }
                    }
                    row.put("outcome", outcome);
                    var stored = jdbc.queryForList(
                            "SELECT query_text FROM consult_requests WHERE origin_message_id=? ORDER BY subquery_order",
                            message);
                    row.put("storedQueries", stored);
                    int expectedStored = row.get("route") instanceof IntentRouteResponse route
                            ? route.subQueries().size() : 0;
                    assertThat(stored).as("응답과 저장된 요청 수: %s", item.path("id").asText())
                            .hasSize(expectedStored);
                    row.put("labelStatus", item.path("labelStatus").asText("REGRESSION"));
                    row.put("expectedRequestCount", item.path("expectedRequestCount")
                            .asInt(item.path("expectedCount").asInt()));
                    row.put("calls", List.copyOf(calls));
                    rows.add(row);
                    groupOutcomes.computeIfAbsent(item.path("id").asText(), ignored -> new ArrayList<>()).add(outcome);
                    write(output, rows);
                    System.out.println("SPACING " + item.path("id").asText() + " " + attempt + " " + outcome);
                }
            }
        }
        if (baselineClass == null) {
            assertThat(failures).isEmpty();
            groupOutcomes.forEach((group, outcomes) -> assertThat(outcomes.stream().distinct().count())
                    .as("띄어쓰기 변형의 결과: %s", group).isEqualTo(1));
        }
    }

    private void write(Path output, List<Map<String, Object>> rows) throws Exception {
        try (var compressed = new GZIPOutputStream(Files.newOutputStream(output))) {
            compressed.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows)
                    .getBytes(StandardCharsets.UTF_8));
        }
    }
}
