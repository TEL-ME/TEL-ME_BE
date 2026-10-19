package com.telme.probe;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.service.ChatContextMessage;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.service.RagAnswerGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * TELME-178 B-1. 실제 채팅 API(앞단: 문맥 판정, 라우팅, 되묻기, 검색)를 통과시키고, 답변 생성 직전의 입력
 * ({@link AnswerRequest}: 질문, 해석된 질문, 확정 조건, 검색 근거, 대화 이력)을 턴마다 저장한다. 저장한 입력은
 * {@link AnswerGenerationProbe}가 읽는 형식으로 바꿔(build_inputs_b.py) 두 모델에 똑같이 넣는다.
 * 앱 코드는 바꾸지 않고, 답변 생성기를 Spring 스파이로 감싸 입력만 가로챈다. 그 턴의 답변은 앱이 평소처럼 만든다.
 *
 * 입력: scripts/answer-generation-evaluation/scenarios-b.json(시나리오마다 고객 발언의 순서)
 * 실행: CAPTURE_PROBE=true POSTGRES_DB=telme_answer_b1 CAPTURE_PROBE_OUT=<결과 jsonl> [CAPTURE_PROBE_GROUPS=묶음1,묶음2 해당 묶음만] [CAPTURE_PROBE_LIMIT=N 묶음마다 앞에서 N개만]
 *       ./gradlew test --tests '*AnswerInputCaptureProbe'
 * 테스트 기본 설정이 끄는 회색 구간 판정기(Bedrock)와 FAQ 되묻기는 이 프로브가 켠다(Bedrock 키 필요: AWS_BEARER_TOKEN_BEDROCK).
 * 앞단 모델은 앱 설정을 따른다. 엑사원은 기본 그대로이고, 앞단을 gpt-oss로 하려면 OLLAMA_URL을 측정용 중계기
 * (scripts/judge-serving-evaluation/judge_router.py --chat bedrock)로 지정하고 CHAT_QUESTION_RESOLUTION_STABILITY_GUARDS=true,
 * ROUTING_REQUEST_INVENTORY_ENABLED=false를 준다. 상담 기록이 쌓이므로 개발 DB가 아닌 복제 DB(telme_answer_b1…)에서만 실행한다.
 */
@SpringBootTest(properties = {
        "llm.provider=ollama",
        "telme.consult.persistence-enabled=true",
        "telme.consult.llm-enabled=true",
        "telme.consult.chat-integration-enabled=true",
        "telme.consult.rag-integration-enabled=true",
        "telme.chat.pipeline.enabled=false",
        "chat.summary.trigger-messages=1000",
        "chat.summary.trigger-tokens=100000",
        // 테스트 기본 설정(src/test/resources/application.properties)은 아래 셋을 끈다. 실제 앞단을 통과시키려고 다시 켠다
        "search.gray-zone.enabled=true",
        "llm.bedrock.enabled=true",
        "telme.consult.faq-clarification.enabled=true"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "CAPTURE_PROBE", matches = "true")
class AnswerInputCaptureProbe {
    private static final Duration EXECUTION_TIMEOUT = Duration.ofMinutes(4);

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private RagAnswerGenerator ragAnswerGenerator;

    private final List<AnswerRequest> captured = new ArrayList<>();

    @Test
    void captureAnswerInputs() throws Exception {
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        if (!database.startsWith("telme_answer_b1")) {
            throw new IllegalStateException("상담 기록이 쌓이므로 telme_answer_b1 복제 DB에서만 실행합니다: " + database);
        }
        doAnswer(invocation -> {
            synchronized (captured) {
                captured.add(invocation.getArgument(0));
            }
            return invocation.callRealMethod();
        }).when(ragAnswerGenerator).generate(any(AnswerRequest.class), any(LlmStreamHandler.class));

        JsonNode scenarios = mapper.readTree(Path.of(
                "scripts/answer-generation-evaluation/scenarios-b.json").toFile());
        int limit = Integer.parseInt(System.getenv().getOrDefault("CAPTURE_PROBE_LIMIT", "0"));
        Path out = Path.of(required("CAPTURE_PROBE_OUT"));
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.deleteIfExists(out);
        Map<String, Integer> perGroup = new LinkedHashMap<>();
        for (JsonNode scenario : scenarios) {
            String groups = System.getenv("CAPTURE_PROBE_GROUPS");
            if (groups != null && !groups.isBlank() && !List.of(groups.split(",")).contains(scenario.path("group").asText())) {
                continue;
            }
            // 소량 점검용: 묶음마다 앞에서 limit개만 돌린다
            if (limit > 0 && perGroup.merge(scenario.path("group").asText(), 1, Integer::sum) > limit) {
                continue;
            }
            MockHttpSession session = new MockHttpSession();
            long sessionId = createSession(session, scenario.path("id").asText());
            int turnIndex = 0;
            for (JsonNode turn : scenario.path("turns")) {
                synchronized (captured) {
                    captured.clear();
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("scenarioId", scenario.path("id").asText());
                row.put("group", scenario.path("group").asText());
                row.put("source", scenario.path("source").asText());
                row.put("turn", turnIndex++);
                row.put("turns", scenario.path("turns").size());
                row.put("question", turn.asText());
                try {
                    row.putAll(runTurn(session, sessionId, turn.asText()));
                } catch (RuntimeException | IOException failure) {
                    row.put("error", failure.toString());
                }
                synchronized (captured) {
                    row.put("answerRequests", captured.stream().map(AnswerInputCaptureProbe::serialize).toList());
                }
                Files.writeString(out, mapper.writeValueAsString(row) + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            System.out.printf("[B-1] %s 턴 %d개 처리%n", scenario.path("id").asText(), scenario.path("turns").size());
        }
    }

    private Map<String, Object> runTurn(MockHttpSession session, long sessionId, String question) throws Exception {
        JsonNode sent = mapper.readTree(mvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("content", question))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
        long executionId = sent.path("result").path("executionId").asLong();
        Map<String, Object> execution = waitForExecution(executionId);
        Long outputMessageId = (Long) execution.get("output_message_id");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executionStatus", execution.get("status"));
        result.put("errorCode", execution.get("error_code"));
        if (outputMessageId != null) {
            Map<String, Object> message = jdbc.queryForMap(
                    "SELECT message_type, answer_basis, content FROM chat_messages WHERE message_id = ?",
                    outputMessageId);
            result.put("outputType", message.get("message_type"));
            result.put("answerBasis", message.get("answer_basis"));
            result.put("outputContent", message.get("content"));
        }
        return result;
    }

    private static Map<String, Object> serialize(AnswerRequest request) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userQuery", request.userQuery());
        row.put("resolvedQuery", request.resolvedQuery());
        row.put("conditions", request.conditions());
        row.put("conditionLabels", request.conditionLabels());
        List<Map<String, Object>> results = new ArrayList<>();
        for (FaqSearchResponse faq : request.searchResults()) {
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("faqId", faq.faqId());
            source.put("slotId", faq.slotId());
            source.put("category", faq.category());
            source.put("question", faq.question());
            source.put("answer", faq.answer());
            source.put("score", faq.score());
            source.put("version", faq.version());
            source.put("matchedVariant", faq.matchedVariant());
            results.add(source);
        }
        row.put("searchResults", results);
        if (request.chatContext() != null) {
            List<Map<String, Object>> history = new ArrayList<>();
            for (ChatContextMessage message : request.chatContext().history()) {
                history.add(Map.of("role", message.role().name(), "messageType", message.messageType().name(),
                        "content", message.content() == null ? "" : message.content()));
            }
            row.put("summary", request.chatContext().summary());
            row.put("history", history);
        }
        return row;
    }

    private long createSession(MockHttpSession session, String scenarioId) throws Exception {
        JsonNode created = mapper.readTree(mvc.perform(post("/api/v1/chat/sessions")
                        .session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("title", "B-1 " + scenarioId))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
        return created.path("result").path("sessionId").asLong();
    }

    private Map<String, Object> waitForExecution(long executionId) throws InterruptedException {
        long deadline = System.nanoTime() + EXECUTION_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT status, error_code, output_message_id FROM chat_executions WHERE execution_id = ?",
                    executionId);
            if (!"RUNNING".equals(row.get("status"))) {
                return row;
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("채팅 실행이 끝나지 않았습니다: executionId=" + executionId);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경 변수가 필요합니다");
        }
        return value;
    }
}
