package com.telme.probe;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.intent.service.ComparisonQuestionPolicy;
import com.telme.intent.service.QueryRoutingService;
import com.telme.intent.service.RoutingPromptTemplates;
import com.telme.intent.service.UnsupportedCompoundQuestionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 별도 평가 DB에서 실제 모델의 질문 분해와 상담 요청 저장을 확인한다.
@SpringBootTest(properties = {
        "llm.provider=ollama",
        "llm.model=exaone3.5:7.8b",
        "telme.consult.chat-integration-enabled=false"
})
@EnabledIfEnvironmentVariable(named = "TELME_SENTENCE_ROUTING_PROBE", matches = "true")
@Transactional
class SentenceRoutingProbe {
    @Autowired QueryRoutingService routing;
    @Autowired ChatMessageRepository messages;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    private static final List<Case> CASES = List.of(
            new Case("SENT-01", "요금제 변경 방법 알려줘. 유심 재발급 방법도 알려줘.",
                    List.of(List.of("요금제", "변경"), List.of("유심", "재발급")), -1),
            new Case("SENT-02", "유심 재발급 방법 알려줘. 요금제 변경 방법도 알려줘.",
                    List.of(List.of("유심", "재발급"), List.of("요금제", "변경")), -1),
            new Case("SENT-03", "요금제 변경 방법 알려줘. 유심 재발급 방법 알려줘.",
                    List.of(List.of("요금제", "변경"), List.of("유심", "재발급")), -1),
            new Case("SENT-04", "요금제 변경 방법 알려주세요. 유심 재발급 방법도 알려주세요.",
                    List.of(List.of("요금제", "변경"), List.of("유심", "재발급")), -1),
            new Case("SENT-05", "로밍 신청 방법 알려주세요\n명의 변경 서류도 알려주세요",
                    List.of(List.of("로밍", "신청"), List.of("명의", "변경", "서류")), -1),
            new Case("SENT-06", "로밍 신청 방법 알려줘. 명의 변경 서류도 알려줘.",
                    List.of(List.of("로밍", "신청"), List.of("명의", "변경", "서류")), -1),
            new Case("SENT-07", "요금제 변경 방법 알려줘. 5G와 LTE 요금제 종류를 비교해줘",
                    List.of(List.of("요금제", "변경"), List.of("5G", "LTE")), 1),
            new Case("SENT-08", "로밍 신청 방법은 뭐야? 5G와 LTE 요금제 종류를 비교해줘",
                    List.of(List.of("로밍", "신청"), List.of("5G", "LTE")), 1),
            new Case("SENT-09", "로밍 신청 방법 알려줘\n5G와 LTE 요금제 종류를 비교해줘",
                    List.of(List.of("로밍", "신청"), List.of("5G", "LTE")), 1),
            new Case("SENT-10", "5G와 LTE 요금제 종류를 비교해줘. 로밍 신청 방법도 알려줘",
                    List.of(List.of("5G", "LTE"), List.of("로밍", "신청")), 0),
            new Case("SENT-11", "지금 5G 요금제를 쓰고 있어. LTE 요금제와 데이터 제공량을 비교해줘.",
                    List.of(List.of("5G", "LTE", "데이터")), 0),
            new Case("SENT-12", "유심을 잃어버렸어. 재발급 방법 알려줘.",
                    List.of(List.of("유심", "재발급")), -1),
            new Case("SENT-13", "로밍 신청 방법 알려줘. 유심 재발급 방법 알려줘. 5G와 LTE 요금제 종류를 비교해줘",
                    List.of(List.of("로밍", "신청"), List.of("유심", "재발급"), List.of("5G", "LTE")), 2));

    @Test
    void runActualSpringRoutingAndRecordMissingRequests() throws Exception {
        long user = jdbc.queryForObject("INSERT INTO users(email,name) VALUES (?,?) RETURNING user_id",
                Long.class, "sentence-probe-" + UUID.randomUUID() + "@example.com", "검증");
        long session = jdbc.queryForObject("INSERT INTO chat_sessions(user_id) VALUES (?) RETURNING session_id",
                Long.class, user);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (Case item : CASES) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                long message = jdbc.queryForObject(
                        "INSERT INTO chat_messages(session_id,sequence_no,role,message_type,content,status)"
                                + " VALUES (?,?,'USER','QUESTION',?,'COMPLETED') RETURNING message_id",
                        Long.class, session, rows.size() + 1, item.question());
                com.telme.intent.dto.res.IntentRouteResponse route = null;
                String guidance = null;
                try {
                    route = routing.routeSingleConsult(messages.findById(message).orElseThrow(), null);
                } catch (UnsupportedCompoundQuestionException rejected) {
                    guidance = rejected.getMessage();
                }
                List<String> issues = new ArrayList<>();
                if (route == null) {
                    // 안내는 성공적인 질문 분해와 구분하고, 일부 상담만 저장되지 않았는지 확인한다.
                    if (jdbc.queryForObject("SELECT count(*) FROM query_routings WHERE message_id=?",
                            Integer.class, message) != 0) issues.add("PARTIAL_ROUTING_AFTER_GUIDANCE");
                } else if (route.subQueries().size() != item.topics().size()) {
                    issues.add("SUB_QUERY_COUNT");
                } else {
                    for (int index = 0; index < item.topics().size(); index++) {
                        String query = route.subQueries().get(index).queryText().replaceAll("\\s+", "");
                        if (!item.topics().get(index).stream().allMatch(query::contains)) {
                            issues.add("MISSING_TOPIC_" + index);
                        }
                    }
                    if (item.comparisonIndex() >= 0 && !ComparisonQuestionPolicy.isStandaloneComparison(
                            route.subQueries().get(item.comparisonIndex()).queryText(), null)) {
                        issues.add("COMPARISON_VALIDATION_BYPASS");
                    }
                }
                var persisted = jdbc.queryForList("SELECT subquery_order, query_text FROM consult_requests"
                        + " WHERE origin_message_id=? ORDER BY subquery_order", message);
                if (persisted.size() != (route == null ? 0 : item.topics().size())) {
                    issues.add("PERSISTED_SUB_QUERY_COUNT");
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.id());
                row.put("question", item.question());
                row.put("attempt", attempt);
                row.put("expectedSubQueryCount", item.topics().size());
                row.put("route", route);
                row.put("routingOutcome", route == null ? "GUIDANCE" : "ROUTED");
                row.put("guidanceReason", guidance);
                row.put("persistedSubQueries", persisted);
                row.put("issues", issues);
                rows.add(row);
                if (!issues.isEmpty()) failures.add(item.id() + ":" + attempt + ":" + issues);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("model", "exaone3.5:7.8b");
        result.put("temperature", 0.1);
        result.put("maxTokens", 500);
        result.put("systemPrompt", RoutingPromptTemplates.ROUTING_SYSTEM_PROMPT);
        Map<String, String> hashes = new LinkedHashMap<>();
        for (String file : List.of("src/main/java/com/telme/intent/service/RoutingPromptTemplates.java",
                "src/main/java/com/telme/intent/service/ComparisonQuestionPolicy.java",
                "src/main/java/com/telme/intent/service/QueryRoutingService.java")) {
            hashes.put(file, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(Path.of(file)))));
        }
        result.put("codeSha256", hashes);
        result.put("records", rows);
        Path output = Path.of(System.getenv().getOrDefault("TELME_SENTENCE_ROUTING_OUT",
                "scripts/compound-faq-evaluation/runs/sentence-routing-current.json"));
        Files.createDirectories(output.getParent());
        Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        assertThat(failures).isEmpty();
    }

    private record Case(String id, String question, List<List<String>> topics, int comparisonIndex) {}
}
