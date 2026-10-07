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
        int noSource = 0;
        long elapsed = 0;
        System.out.println("EVAL|" + ConditionPromptTemplates.PROMPT_VERSION);

        for (JsonNode each : cases) {
            String question = each.get("question").asText();
            int expected = each.get("expect").asInt();
            var sources = searches.search(new FaqSearchRequest(question, 3));
            if (sources.isEmpty()) {
                // 검색이 비면 뽑기를 돌리지 않아 조건이 없다. 뽑기 성능과 섞으면 점수가 부풀어 오른다
                noSource++;
                System.out.printf("EVAL|검색없음|%s|기대 %d|나온 것 0|%n", question, each.get("expect").asInt());
                continue;
            }

            long startedAt = System.currentTimeMillis();
            // 평가 호출은 실제 채팅 실행이 아니라 llm_generations에 남기지 않는다
            List<MissingCondition> conditions = extractor.extract(null, question, sources);
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
            System.out.printf("EVAL|%s|%s|기대 %d(%s)|나온 것 %d|%s%n",
                    verdict, question, expected, each.get("about").asText(), conditions.size(),
                    conditions.stream().map(ConditionExtractionEval::asked)
                            .reduce((a, b) -> a + " / " + b).orElse(""));
        }

        int scored = hit + missed + invented;
        // 조건 유무만 센다. 뽑은 조건이 기대한 조건과 같은지는 about과 나온 질문을 눈으로 비교한다
        System.out.printf("EVAL_SUM|맞음 %d|놓침 %d|헛짚음 %d|검색없음 %d|정확도 %.0f%%|평균 %dms%n",
                hit, missed, invented, noSource,
                scored == 0 ? 0 : hit * 100.0 / scored, scored == 0 ? 0 : elapsed / scored);
        assertThat(scored + noSource).isEqualTo(cases.size());
    }

    // 질문과 선택지가 맞는지 보려면 둘을 같이 찍어야 한다
    private static String asked(MissingCondition condition) {
        return condition.question()
                + (condition.options().isEmpty() ? " [직접입력]" : " " + condition.options());
    }
}
