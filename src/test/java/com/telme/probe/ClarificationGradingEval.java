package com.telme.probe;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.dto.MissingCondition;
import com.telme.consult.service.ConditionExtractor;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.service.LlmClient;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

// 정답 근거를 찾고도 되묻기로 끝난 질문을 근거 직접 입력으로 채점(검색·판정기 제외)
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LLM_PROVIDER", matches = "ollama")
class ClarificationGradingEval {

    private static final String CASES = "/condition-eval/grading-cases.json";

    @Autowired private LlmClient llmClient;
    @Autowired(required = false) @Qualifier("judgeLlmClient") private LlmClient judgeClient;

    @Test
    @DisplayName("채점 문항으로 불필요한 되묻기를 채점한다")
    void 채점한다() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ConditionExtractor extractor = new ConditionExtractor(extractionClient(), objectMapper);
        JsonNode cases;
        try (InputStream stream = getClass().getResourceAsStream(CASES)) {
            cases = objectMapper.readTree(stream).get("cases");
        }

        int ok = 0;
        int unnecessary = 0;
        int missed = 0;
        int reference = 0;
        for (JsonNode each : cases) {
            List<FaqSearchResponse> sources = new ArrayList<>();
            int rank = 1;
            for (JsonNode source : each.get("sources")) {
                sources.add(new FaqSearchResponse((long) rank, source.get("slot").asText(),
                        source.get("slot").asText().split("-")[0],
                        source.get("question").asText(), source.get("answer").asText(),
                        0.8, 1, null, rank, null));
                rank++;
            }
            String question = each.get("question").asText();
            List<MissingCondition> conditions = extractor.extract(null, question, sources);

            int expected = each.get("expect").asInt();
            String verdict;
            if (expected < 0) {
                verdict = "참고";
                reference++;
            } else if (expected == 0 && !conditions.isEmpty()) {
                verdict = "불필요";
                unnecessary++;
            } else if (expected > 0 && conditions.isEmpty()) {
                verdict = "놓침";
                missed++;
            } else {
                verdict = "맞음";
                ok++;
            }
            System.out.printf("GRADE|%s|%s|%s|%s|%s|나온 것 %d|%s%n",
                    verdict, each.get("id").asText(), each.get("kind").asText(), question,
                    each.get("about").asText(), conditions.size(),
                    conditions.stream().map(MissingCondition::question)
                            .reduce((a, b) -> a + " / " + b).orElse(""));
        }

        int scored = ok + unnecessary + missed;
        System.out.printf("GRADE_SUM|맞음 %d|불필요 %d|놓침 %d|참고 %d|정확도 %.0f%%%n",
                ok, unnecessary, missed, reference, scored == 0 ? 0 : ok * 100.0 / scored);
        if (counted != null) {
            System.out.println("BEDROCK_CALLS|" + counted.calls());
        }
        assertThat(scored + reference).isEqualTo(cases.size());
    }

    private CountingLlmClient counted;


    // EVAL_EXTRACTOR=bedrock이면 조건 뽑기를 Bedrock으로(LLM_BEDROCK_ENABLED=true 필요, 유료)
    private LlmClient extractionClient() {
        if ("bedrock".equals(System.getenv("EVAL_EXTRACTOR")) && judgeClient != null) {
            counted = new CountingLlmClient(judgeClient);
            return counted;
        }
        return llmClient;
    }

}
