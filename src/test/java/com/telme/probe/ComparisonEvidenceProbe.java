package com.telme.probe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.dto.res.AnswerResult;
import com.telme.rag.service.AnswerGenerator;
import com.telme.llm.service.LlmStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Opt-in live comparison evidence and answer probe. */
@SpringBootTest(properties = {
        "llm.provider=ollama",
        "telme.consult.chat-integration-enabled=false",
        "rag.evidence-check.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "TELME_COMPARISON_PROBE", matches = "true")
class ComparisonEvidenceProbe {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final List<Case> CASES = List.of(
            new Case("CMP-001", "5G와 LTE 요금제 종류를 비교해줘", "5G 요금제 종류", "LTE 요금제 종류"),
            new Case("CMP-002", "너겟 5G 요금제와 LTE 요금제의 데이터 제공량 차이를 비교해줘", "너겟 5G 요금제 데이터 제공량", "LTE 요금제 데이터 제공량"),
            new Case("CMP-003", "월 5만 원대 요금제에서 데이터와 속도 제한 조건을 비교해줘", "월 5만원대 요금제 데이터 제공량", "월 5만원대 요금제 속도 제한 조건"),
            new Case("CMP-004", "eSIM과 유심의 개통 절차를 비교해줘", "eSIM 개통 절차", "유심 개통 절차"),
            new Case("CMP-005", "명의 변경과 번호 이동의 필요 서류를 비교해줘", "명의 변경 필요 서류", "번호 이동 필요 서류"),
            new Case("CMP-006", "선택약정과 공시지원금 할인 차이를 비교해줘", "선택약정 할인", "공시지원금 할인"),
            new Case("CMP-007", "해외 로밍 패스와 데이터 로밍 종량제의 요금 및 제공량을 비교해줘", "해외 로밍 패스 요금과 제공량", "데이터 로밍 종량제 요금과 제공량"),
            new Case("CMP-008", "일반 요금제와 청소년 요금제의 가입 조건을 비교해줘", "일반 요금제 가입 조건", "청소년 요금제 가입 조건"),
            new Case("CMP-009", "인터넷 결합 할인과 가족 결합 할인 차이를 비교해서 알려줘", "인터넷 결합 할인 조건", "가족 결합 할인 조건"),
            new Case("CMP-010", "분실 신고와 일시 정지의 차이와 이용 제한을 비교해줘", "분실 신고 이용 제한", "일시 정지 이용 제한"),
            new Case("CMP-011", "물리 유심 재발급과 eSIM 발급 비용을 비교해줘", "물리 유심 재발급 비용", "eSIM 발급 비용"),
            new Case("CMP-012", "청소년 요금제와 시니어 요금제의 가입 연령을 비교해줘", "청소년 요금제 가입 연령", "시니어 요금제 가입 연령"),
            new Case("CMP-013", "5G 라이트와 LTE 플러스의 월 요금과 데이터 제공량을 비교해줘", "5G 라이트 월 요금 데이터 제공량", "LTE 플러스 월 요금 데이터 제공량"),
            new Case("CMP-014", "일시 정지와 해지의 요금 차이를 비교해줘", "일시 정지 기본료와 기간 제한", "해지 요금 일할 계산")
    );

    @Autowired private FaqSearchService search;
    @Autowired private AnswerGenerator answerGenerator;

    @Test
    void runComparisonProbe() throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Case c : CASES) {
            List<FaqSearchResponse> raw = search.search(new FaqSearchRequest(c.question, 3));
            List<FaqSearchResponse> left = search.search(new FaqSearchRequest(c.leftQuery, 3));
            List<FaqSearchResponse> right = search.search(new FaqSearchRequest(c.rightQuery, 3));
            List<FaqSearchResponse> merged = merge(left, right);
            List<FaqSearchResponse> expanded = merge(raw, left, right);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("eval_id", c.id);
            row.put("question", c.question);
            row.put("left_query", c.leftQuery);
            row.put("right_query", c.rightQuery);
            row.put("raw_sources", serialize(raw));
            row.put("left_sources", serialize(left));
            row.put("right_sources", serialize(right));
            row.put("merged_sources", serialize(merged));
            row.put("expanded_sources", serialize(expanded));
            row.put("raw_answer", generate(c.question, raw));
            row.put("merged_answer", generate(c.question, merged));
            row.put("expanded_answer", generate(c.question, expanded));
            results.add(row);
            persist(results);
        }
        persist(results);
    }

    private String generate(String question, List<FaqSearchResponse> sources) {
        try {
            AnswerResult result = answerGenerator.generate(AnswerRequest.builder()
                    .userQuery(question).searchResults(sources).build(), new SilentHandler());
            return result.answer();
        } catch (RuntimeException e) {
            return "[GENERATION_ERROR] " + e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    @SafeVarargs
    private final List<FaqSearchResponse> merge(List<FaqSearchResponse>... groups) {
        Map<Long, FaqSearchResponse> unique = new LinkedHashMap<>();
        for (List<FaqSearchResponse> group : groups) {
            group.forEach(f -> unique.putIfAbsent(f.faqId(), f));
        }
        return List.copyOf(unique.values());
    }

    private List<Map<String, Object>> serialize(List<FaqSearchResponse> sources) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FaqSearchResponse f : sources) {
            rows.add(Map.of("faq_id", f.faqId(), "score", f.score(), "question", f.question(), "answer", f.answer()));
        }
        return rows;
    }

    private void persist(List<Map<String, Object>> results) throws Exception {
        Path output = Path.of(System.getenv().getOrDefault("TELME_COMPARISON_OUT", ".measure/comparison-evidence.json"));
        Files.createDirectories(output.getParent());
        Files.writeString(output, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(results));
    }

    private record Case(String id, String question, String leftQuery, String rightQuery) {}

    private static final class SilentHandler implements LlmStreamHandler {
        @Override public void onToken(String token) {}
        @Override public void onComplete() {}
        @Override public void onError(Throwable error) {}
    }
}
