package com.telme.consult.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.config.PlanQuestionConfiguration;
import com.telme.consult.config.PlanQuestionProperties;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import com.telme.plan.entity.Plan;
import com.telme.plan.repository.PlanRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

// LLM 분류가 기본이고, 꺼져 있거나 분류하지 못했을 때만 규칙 분류를 쓴다
@Slf4j
@Component
public class PlanQuestionClassifier {

    // gpt-oss(Bedrock)는 답 앞의 내부 추론도 출력 한도를 쓴다. 엑사원은 스키마로 답만 내서 한도와 관계없이 짧다
    private static final int MAX_TOKENS = 512;
    private static final Pattern RELATED = Pattern.compile("요금|플랜|데이터|통화|전화|문자|사용량|약정|위약금|해지|가입|추천|5G|LTE|스탠다드|프리미엄|라이트|베이직|언리미티드|알뜰|청소년|시니어|갈아|바꾸|저렴|싼|영상|유튜브|넷플릭스|게임|SNS|스트리밍|화상|와이파이|폰");

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final PlanQuestionProperties properties;
    private final PlanRepository planRepository;

    // 분류 모델은 PlanQuestionConfiguration이 고른다(telme.consult.plan-question.bedrock)
    public PlanQuestionClassifier(@Qualifier(PlanQuestionConfiguration.PLAN_QUESTION_LLM_CLIENT) LlmClient llmClient,
            ObjectMapper objectMapper, PlanQuestionProperties properties, PlanRepository planRepository) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.planRepository = planRepository;
    }

    public PlanQuestionType classify(Long executionId, String question) {
        return classifyQuestion(executionId, question).type();
    }

    // 요금제 목록 질문이면 모델이 고른 요금제(mention)도 함께 돌려준다. 규칙으로 분류하면 mention은 없다
    public PlanQuestion classifyQuestion(Long executionId, String question) {
        if (question == null || question.isBlank()) {
            return PlanQuestion.of(PlanQuestionType.NONE);
        }
        if (!properties.enabled()) {
            return PlanQuestion.of(PlanQuestionRules.classify(question));
        }
        if (!mentionsPlanTopic(question)) {
            return PlanQuestion.of(PlanQuestionType.NONE);
        }
        try {
            List<Plan> plans = planRepository.findByStatusOrderBySortOrderAscPlanIdAsc(Plan.Status.ACTIVE);
            Reply reply = parse(llmClient.generate(request(executionId, question, plans)));
            if (reply != null) {
                PlanQuestionType type = guard(reply.type(), question);
                return new PlanQuestion(type, type == PlanQuestionType.PLAN_CATALOG ? mention(reply, plans) : null);
            }
            log.info("[요금제 질문 분류] 응답을 읽지 못해 규칙으로 분류합니다. executionId={}", executionId);
        } catch (RuntimeException exception) {
            log.warn("[요금제 질문 분류] 모델 호출에 실패해 규칙으로 분류합니다. executionId={}", executionId, exception);
        }
        return PlanQuestion.of(PlanQuestionRules.classify(question));
    }

    private record Reply(PlanQuestionType type, List<String> planCodes, boolean unknownPlan) {
    }

    private static final Set<String> REPLY_FIELDS = Set.of("type", "plans", "unknownPlan");

    // 판매 중이 아닌 코드는 버린다. 목록이 없으면 규칙으로 요금제 이름을 찾게 mention을 두지 않는다
    private static PlanQuestion.Mention mention(Reply reply, List<Plan> plans) {
        if (plans.isEmpty()) {
            return null;
        }
        Set<String> active = plans.stream().map(Plan::getCode).collect(Collectors.toSet());
        List<String> codes = reply.planCodes().stream().filter(active::contains).distinct().toList();
        return new PlanQuestion.Mention(reply.unknownPlan() ? List.of() : codes, reply.unknownPlan());
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
            return PlanQuestionRules.hasPersonalMarker(type, question) && !PlanQuestionRules.asksMethod(type, question)
                    ? type : PlanQuestionType.NONE;
        }
        boolean listOrRecommend = type == PlanQuestionType.PLAN_CATALOG || type == PlanQuestionType.RECOMMENDATION;
        return listOrRecommend && PlanQuestionRules.asksMethod(type, question) ? PlanQuestionType.NONE : type;
    }

    private static boolean isMyData(PlanQuestionType type) {
        return type == PlanQuestionType.MY_SUBSCRIPTION || type == PlanQuestionType.MY_PENALTY
                || type == PlanQuestionType.MY_USAGE;
    }

    static boolean mentionsPlanTopic(String question) {
        return RELATED.matcher(question).find();
    }

    static Map<String, Object> schema(List<Plan> plans) {
        List<String> types = Arrays.stream(PlanQuestionType.values()).map(Enum::name).toList();
        Map<String, Object> planItem = plans.isEmpty() ? Map.of("type", "string")
                : Map.of("type", "string", "enum", plans.stream().map(Plan::getCode).toList());
        return Map.of("type", "object",
                "properties", Map.of("type", Map.of("type", "string", "enum", types),
                        "plans", Map.of("type", "array", "items", planItem),
                        "unknownPlan", Map.of("type", "boolean")),
                "required", List.of("type", "plans", "unknownPlan"), "additionalProperties", false);
    }

    private LlmRequest request(Long executionId, String question, List<Plan> plans) {
        return LlmRequest.builder()
                .executionId(executionId)
                .taskType(TaskType.ROUTING)
                .systemPrompt(PlanQuestionPromptTemplates.systemPrompt(plans))
                .userPrompt(question)
                .temperature(0.0)
                .maxTokens(MAX_TOKENS)
                .promptVersion(PlanQuestionPromptTemplates.PROMPT_VERSION)
                .responseSchema(schema(plans))
                .think(false)
                .build();
    }

    // type은 꼭 있어야 한다. plans·unknownPlan이 없거나 형식이 다르면 요금제를 고르지 않은 것으로 본다
    private Reply parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject() || !root.path("type").isTextual()) {
                return null;
            }
            for (Iterator<String> names = root.fieldNames(); names.hasNext(); ) {
                if (!REPLY_FIELDS.contains(names.next())) {
                    return null;
                }
            }
            List<String> codes = new ArrayList<>();
            root.path("plans").forEach(code -> {
                if (code.isTextual()) {
                    codes.add(code.textValue());
                }
            });
            return new Reply(PlanQuestionType.valueOf(root.path("type").textValue()), codes,
                    root.path("unknownPlan").asBoolean(false));
        } catch (Exception exception) {
            return null;
        }
    }
}
