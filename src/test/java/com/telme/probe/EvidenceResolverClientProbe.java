package com.telme.probe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.service.LlmComparisonEvidenceResolver;
import com.telme.consult.service.LlmFaqCandidateEvidenceResolver;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.service.LlmClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * TELME-178 비교 근거 검증(LlmComparisonEvidenceResolver)과 FAQ 후보 근거 검증(LlmFaqCandidateEvidenceResolver)을
 * 엑사원(Ollama)과 gpt-oss(Bedrock)로 같은 질문에 돌려 고른 FAQ와 모델 원문 출력을 저장한다. 채점은 하지 않는다.
 * 검색은 앱의 실제 검색(로컬 DB·bge-m3)을 쓰고, 두 호출만 선택한 모델로 바꿔 직접 만든다. 앱 설정은 바꾸지 않는다.
 *
 * 입력: scripts/evidence-resolver-evaluation/comparison-cases.json(비교 질문 19개),
 *       candidate-cases.json(원문 검색이 비었던 질문 107개)
 * 실행: RESOLVER_PROBE=true RESOLVER_PROBE_OUT=<결과 jsonl> [RESOLVER_PROBE_MODELS=exaone,gptoss]
 *       [RESOLVER_PROBE_KINDS=comparison,candidate] [RESOLVER_PROBE_EXAONE_REPEATS=1] [RESOLVER_PROBE_GPTOSS_REPEATS=3]
 *       [RESOLVER_PROBE_LIMIT=N 질문 종류마다 앞에서 N개만]
 *       ./gradlew test --tests '*EvidenceResolverClientProbe'
 * 모델 클라이언트 환경 변수는 {@link ProbeModelClients} 참고.
 */
@SpringBootTest(properties = {
        "llm.provider=ollama",
        "telme.consult.chat-integration-enabled=false",
        "rag.evidence-check.enabled=false"
})
@EnabledIfEnvironmentVariable(named = "RESOLVER_PROBE", matches = "true")
class EvidenceResolverClientProbe {
    private static final int CANDIDATE_TOP_K = 10;
    private static final int SEARCH_TOP_K = 3;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Autowired private FaqSearchService search;

    @Test
    void runResolvers() throws Exception {
        Path out = Path.of(required("RESOLVER_PROBE_OUT"));
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.deleteIfExists(out);
        List<String> models = list("RESOLVER_PROBE_MODELS", List.of("exaone", "gptoss"));
        List<String> kinds = list("RESOLVER_PROBE_KINDS", List.of("comparison", "candidate"));
        int limit = Integer.parseInt(System.getenv().getOrDefault("RESOLVER_PROBE_LIMIT", "0"));
        JsonNode comparison = cases("scripts/evidence-resolver-evaluation/comparison-cases.json", limit);
        JsonNode candidate = cases("scripts/evidence-resolver-evaluation/candidate-cases.json", limit);
        for (String model : models) {
            int repeats = Integer.parseInt(System.getenv().getOrDefault(
                    "RESOLVER_PROBE_" + model.toUpperCase() + "_REPEATS", "gptoss".equals(model) ? "3" : "1"));
            for (int run = 1; run <= repeats; run++) {
                if (kinds.contains("comparison")) {
                    for (JsonNode testCase : comparison) {
                        append(out, comparisonRow(model, run, testCase));
                    }
                }
                if (kinds.contains("candidate")) {
                    for (JsonNode testCase : candidate) {
                        append(out, candidateRow(model, run, testCase));
                    }
                }
            }
        }
    }

    private Map<String, Object> comparisonRow(String model, int run, JsonNode testCase) {
        List<Map<String, Object>> calls = new ArrayList<>();
        LlmClient client = ProbeModelClients.create(model, mapper, calls, true);
        var resolver = new LlmComparisonEvidenceResolver(client, mapper);
        String question = testCase.path("question").asText();
        Map<String, Object> row = base("comparison", model, run, testCase);
        row.put("goldFaqIds", testCase.path("goldFaqIds"));
        row.put("goldSlotIds", testCase.path("goldSlotIds"));
        List<Map<String, Object>> targeted = new ArrayList<>();
        try {
            List<FaqSearchResponse> original = search.search(new FaqSearchRequest(question, SEARCH_TOP_K));
            var resolution = resolver.resolveDetailed(null, question, original, (query, kind) -> {
                List<FaqSearchResponse> found = search.searchCandidates(new FaqSearchRequest(query, CANDIDATE_TOP_K));
                targeted.add(Map.of("kind", kind, "query", query, "sources", summaries(found)));
                return found;
            });
            row.put("originalSources", summaries(original));
            row.put("selectedSources", summaries(resolution.sources()));
            row.put("answerMethod", resolution.answer() == null ? "NO_EVIDENCE" : "VERIFIED_FAQ_QUOTE");
            row.put("answer", resolution.answer());
        } catch (RuntimeException failure) {
            row.put("error", failure.toString());
        }
        row.put("targetedSearches", targeted);
        row.put("calls", calls);
        return row;
    }

    private Map<String, Object> candidateRow(String model, int run, JsonNode testCase) {
        List<Map<String, Object>> calls = new ArrayList<>();
        LlmClient client = ProbeModelClients.create(model, mapper, calls, true);
        var resolver = new LlmFaqCandidateEvidenceResolver(client, mapper);
        String question = testCase.path("question").asText();
        Map<String, Object> row = base("candidate", model, run, testCase);
        row.put("goldSlotIds", testCase.path("goldSlotIds"));
        try {
            List<FaqSearchResponse> candidates = search.searchCandidates(new FaqSearchRequest(question, CANDIDATE_TOP_K));
            FaqSearchResponse picked = resolver.resolve(null, null, question, candidates);
            row.put("candidates", summaries(candidates));
            row.put("picked", picked == null ? null : summaries(List.of(picked)).get(0));
        } catch (RuntimeException failure) {
            row.put("error", failure.toString());
        }
        row.put("calls", calls);
        return row;
    }

    private Map<String, Object> base(String kind, String model, int run, JsonNode testCase) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("kind", kind);
        row.put("id", testCase.path("id").asText());
        row.put("model", model);
        row.put("run", run);
        row.put("question", testCase.path("question").asText());
        return row;
    }

    private static List<Map<String, Object>> summaries(List<FaqSearchResponse> sources) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (FaqSearchResponse source : sources) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("faqId", source.faqId());
            row.put("slotId", source.slotId());
            row.put("score", source.score());
            row.put("question", source.question());
            row.put("answer", source.answer());
            rows.add(row);
        }
        return rows;
    }

    private JsonNode cases(String path, int limit) throws Exception {
        JsonNode all = mapper.readTree(Files.readString(Path.of(path))).path("cases");
        if (limit <= 0) {
            return all;
        }
        var limited = mapper.createArrayNode();
        for (int i = 0; i < Math.min(limit, all.size()); i++) {
            limited.add(all.get(i));
        }
        return limited;
    }

    private void append(Path out, Map<String, Object> row) throws Exception {
        Files.writeString(out, mapper.writeValueAsString(row) + "\n",
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 환경 변수가 필요합니다");
        }
        return value;
    }

    private static List<String> list(String name, List<String> fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : List.of(value.split(","));
    }
}
