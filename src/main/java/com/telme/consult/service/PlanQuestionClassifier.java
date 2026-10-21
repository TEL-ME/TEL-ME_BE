package com.telme.consult.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.config.PlanQuestionConfiguration;
import com.telme.consult.config.PlanQuestionProperties;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

// LLM 분류가 기본이고, 꺼져 있거나 분류하지 못했을 때만 규칙 분류를 쓴다
@Slf4j
@Component
public class PlanQuestionClassifier {

    // gpt-oss(Bedrock)는 답 앞의 내부 추론도 출력 한도를 쓴다. 엑사원은 스키마로 답만 내서 한도와 관계없이 짧다
    private static final int MAX_TOKENS = 512;
    private static final Pattern RELATED = Pattern.compile("요금|플랜|데이터|통화|문자|사용량|약정|위약금|해지|가입|추천|5G|LTE|스탠다드|프리미엄|라이트|베이직|언리미티드|알뜰|청소년|시니어|갈아|바꾸|저렴|싼|영상|유튜브|넷플릭스|게임|SNS|스트리밍|화상|와이파이|폰");

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final PlanQuestionProperties properties;

    // 분류 모델은 PlanQuestionConfiguration이 고른다(telme.consult.plan-question.bedrock)
    public PlanQuestionClassifier(@Qualifier(PlanQuestionConfiguration.PLAN_QUESTION_LLM_CLIENT) LlmClient llmClient,
            ObjectMapper objectMapper, PlanQuestionProperties properties) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public PlanQuestionType classify(Long executionId, String question) {
        if (question == null || question.isBlank()) {
            return PlanQuestionType.NONE;
        }
        if (!properties.enabled()) {
            return PlanQuestionRules.classify(question);
        }
        if (!mentionsPlanTopic(question)) {
            return PlanQuestionType.NONE;
        }
        try {
            PlanQuestionType classified = parse(llmClient.generate(request(executionId, question)));
            if (classified != null) {
                return guard(classified, question);
            }
            log.info("[요금제 질문 분류] 응답을 읽지 못해 규칙으로 분류합니다. executionId={}", executionId);
        } catch (RuntimeException exception) {
            log.warn("[요금제 질문 분류] 모델 호출에 실패해 규칙으로 분류합니다. executionId={}", executionId, exception);
        }
        return PlanQuestionRules.classify(question);
    }

    private static PlanQuestionType guard(PlanQuestionType type, String question) {
        if (type != PlanQuestionType.NONE && PlanQuestionRules.asksOtherService(question)) {
            return PlanQuestionType.NONE;
        }
        if (type == PlanQuestionType.PLAN_CATALOG && PlanQuestionRules.asksDailyFee(question)) {
            return PlanQuestionType.NONE;
        }
        if (type == PlanQuestionType.RECOMMENDATION && PlanQuestionRules.recommendsOtherItem(question)) {
            return PlanQuestionType.NONE;
        }
        if (isMyData(type)) {
            return PlanQuestionRules.hasPersonalMarker(type, question) && !PlanQuestionRules.asksProcedure(type, question)
                    ? type : PlanQuestionType.NONE;
        }
        boolean listOrRecommend = type == PlanQuestionType.PLAN_CATALOG || type == PlanQuestionType.RECOMMENDATION;
        return listOrRecommend && PlanQuestionRules.asksProcedure(type, question) ? PlanQuestionType.NONE : type;
    }

    private static boolean isMyData(PlanQuestionType type) {
        return type == PlanQuestionType.MY_SUBSCRIPTION || type == PlanQuestionType.MY_PENALTY
                || type == PlanQuestionType.MY_USAGE;
    }

    static boolean mentionsPlanTopic(String question) {
        return RELATED.matcher(question).find();
    }

    static Map<String, Object> schema() {
        List<String> types = Arrays.stream(PlanQuestionType.values()).map(Enum::name).toList();
        return Map.of("type", "object", "properties", Map.of("type", Map.of("type", "string", "enum", types)),
                "required", List.of("type"), "additionalProperties", false);
    }

    private LlmRequest request(Long executionId, String question) {
        return LlmRequest.builder()
                .executionId(executionId)
                .taskType(TaskType.ROUTING)
                .systemPrompt(PlanQuestionPromptTemplates.SYSTEM_PROMPT)
                .userPrompt(question)
                .temperature(0.0)
                .maxTokens(MAX_TOKENS)
                .promptVersion(PlanQuestionPromptTemplates.PROMPT_VERSION)
                .responseSchema(schema())
                .think(false)
                .build();
    }

    private PlanQuestionType parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject() || root.size() != 1 || !root.path("type").isTextual()) {
                return null;
            }
            return PlanQuestionType.valueOf(root.path("type").textValue());
        } catch (Exception exception) {
            return null;
        }
    }
}
