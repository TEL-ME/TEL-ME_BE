package com.telme.probe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.entity.Faq;
import com.telme.faq.repository.FaqRepository;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.service.AnswerGenerator;
import com.telme.llm.service.LlmStreamHandler;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

// 사람이 확인한 FAQ를 직접 넣어 비교 답변을 생성한다. 벡터 검색은 거치지 않는다.
@SpringBootTest(properties = {
        "llm.provider=ollama",
        "telme.consult.chat-integration-enabled=false",
        "rag.evidence-check.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "TELME_FORCED_EVIDENCE_PROBE", matches = "true")
class ForcedEvidenceComparisonProbe {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final List<Case> CASES = List.of(
            new Case("CMP-001", "5G와 LTE 요금제 종류를 비교해줘", List.of(117L),
                    List.of("5G 요금제 종류 수", "LTE 요금제 종류 수")),
            new Case("CMP-005", "명의 변경과 번호 이동의 필요 서류를 비교해줘", List.of(65L, 125L),
                    List.of("명의 변경 서류", "번호 이동 서류")),
            new Case("CMP-010", "분실 신고와 일시 정지의 차이와 이용 제한을 비교해줘",
                    List.of(45L, 637L), List.of("분실 신고 시 즉시 정지", "일시 정지 기간", "일시 정지 요금")),
            new Case("CMP-011", "물리 유심 재발급과 eSIM 발급 비용을 비교해줘", List.of(297L),
                    List.of("물리 유심 비용", "eSIM 비용")),
            new Case("CMP-012", "청소년 요금제와 시니어 요금제의 가입 연령을 비교해줘", List.of(121L),
                    List.of("청소년 가입 연령", "시니어 가입 연령")),
            new Case("CMP-013", "5G 라이트와 LTE 플러스의 월 요금과 데이터 제공량을 비교해줘",
                    List.of(1001L), List.of("5G 라이트 요금", "5G 라이트 데이터", "LTE 플러스 요금", "LTE 플러스 데이터")),
            new Case("CMP-014", "일시 정지와 해지의 요금 차이를 비교해줘", List.of(697L),
                    List.of("일시 정지 요금", "해지 요금")));

    @Autowired private FaqRepository faqRepository;
    @Autowired private AnswerGenerator answerGenerator;
    @Value("${llm.model:exaone3.5:7.8b}") private String model;

    @Test
    void evaluateComparisonAnswersWithGoldFaqEvidence() throws Exception {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Case testCase : CASES) {
            List<Faq> faqs = faqRepository.findAllById(testCase.faqIds());
            Map<Long, Faq> byId = new LinkedHashMap<>();
            faqs.forEach(faq -> byId.put(faq.getFaqId(), faq));
            if (byId.size() != testCase.faqIds().size()
                    || faqs.stream().anyMatch(faq -> faq.getStatus() != Faq.Status.ACTIVE)) {
                throw new IllegalStateException("Gold FAQ is missing or inactive: " + testCase.id());
            }

            List<FaqSearchResponse> evidence = new ArrayList<>();
            for (int i = 0; i < testCase.faqIds().size(); i++) {
                Faq faq = byId.get(testCase.faqIds().get(i));
                evidence.add(new FaqSearchResponse(faq.getFaqId(), faq.getSlotId(), faq.getCategory(),
                        faq.getQuestion(), faq.getAnswer(), 1.0, faq.getVersion(),
                        faq.getUpdatedAt() == null ? LocalDate.now()
                                : faq.getUpdatedAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate(),
                        i + 1, "FORCED_GOLD"));
            }

            long startedAt = System.nanoTime();
            var answer = answerGenerator.generate(AnswerRequest.builder()
                    .userQuery(testCase.question())
                    .conditions(Map.of())
                    .searchResults(evidence)
                    .build(), new SilentHandler());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("eval_id", testCase.id());
            row.put("question", testCase.question());
            row.put("retrieval_bypassed", true);
            row.put("evidence", evidence.stream().map(source -> Map.of(
                    "faq_id", source.faqId(), "question", source.question(), "answer", source.answer()))
                    .toList());
            row.put("expected_facts", testCase.expectedFacts());
            row.put("answer", answer.answer());
            row.put("answer_basis", answer.answerBasis());
            row.put("elapsed_ms", (System.nanoTime() - startedAt) / 1_000_000);
            row.put("model", model);
            results.add(row);
            persist(results);
        }
        persist(results);
    }

    private void persist(List<Map<String, Object>> results) throws Exception {
        Path output = Path.of(System.getenv().getOrDefault(
                "TELME_FORCED_EVIDENCE_OUT",
                "scripts/compound-faq-evaluation/runs/comparison-forced-evidence-current.json"));
        Files.createDirectories(output.getParent());
        Files.writeString(output, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(results));
    }

    private record Case(String id, String question, List<Long> faqIds, List<String> expectedFacts) {}

    private static final class SilentHandler implements LlmStreamHandler {
        @Override public void onToken(String token) {}
        @Override public void onComplete() {}
        @Override public void onError(Throwable error) {}
    }
}
