package com.telme.probe;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.dto.MissingCondition;
import com.telme.consult.service.ConditionExtractor;
import com.telme.consult.service.ConditionPromptTemplates;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.service.FaqSearchService;
import com.telme.llm.service.LlmClient;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 조건 뽑기를 평가셋으로 채점한다. 프롬프트를 고칠 때마다 돌려 점수를 비교한다.
 * 실제 모델을 부르므로 LLM_PROVIDER=ollama 일 때만 돈다.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LLM_PROVIDER", matches = "ollama")
class ConditionExtractionEval {

    private static final String CASES = "/condition-eval/cases.json";

    @Autowired private FaqSearchService searches;
    @Autowired private LlmClient llmClient;

    @Test
    @DisplayName("평가셋으로 조건 뽑기를 채점한다")
    void 채점한다() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        ConditionExtractor extractor = new ConditionExtractor(llmClient, objectMapper);
        JsonNode cases;
        try (InputStream stream = getClass().getResourceAsStream(CASES)) {
            cases = objectMapper.readTree(stream).get("cases");
        }

        int hit = 0;
        int missed = 0;
        int invented = 0;
        long elapsed = 0;
        System.out.println("EVAL|" + ConditionPromptTemplates.PROMPT_VERSION);

        for (JsonNode each : cases) {
            String question = each.get("question").asText();
            int expected = each.get("expect").asInt();
            var sources = searches.search(new FaqSearchRequest(question, 3));

            long startedAt = System.currentTimeMillis();
            List<MissingCondition> conditions = extractor.extract(1L, question, sources);
            elapsed += System.currentTimeMillis() - startedAt;

            String verdict;
            if (expected > 0 && conditions.isEmpty()) {
                verdict = "놓침";
                missed++;
            } else if (expected == 0 && !conditions.isEmpty()) {
                verdict = "헛짚음";
                invented++;
            } else {
                verdict = "맞음";
                hit++;
            }
            System.out.printf("EVAL|%s|%s|기대 %d|나온 것 %d|%s%n",
                    verdict, question, expected, conditions.size(),
                    conditions.stream().map(MissingCondition::question).reduce((a, b) -> a + " / " + b).orElse(""));
        }

        System.out.printf("EVAL_SUM|맞음 %d|놓침 %d|헛짚음 %d|정확도 %.0f%%|평균 %dms%n",
                hit, missed, invented, hit * 100.0 / cases.size(), elapsed / cases.size());
        assertThat(hit + missed + invented).isEqualTo(cases.size());
    }
}
