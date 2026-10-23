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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
                PlanQuestion.Mention mention = type == PlanQuestionType.PLAN_CATALOG ? mention(reply, plans) : null;
                return new PlanQuestion(type, withListConditions(executionId, question, mention, plans));
            }
            log.info("[요금제 질문 분류] 응답을 읽지 못해 규칙으로 분류합니다. executionId={}", executionId);
        } catch (RuntimeException exception) {
            log.warn("[요금제 질문 분류] 모델 호출에 실패해 규칙으로 분류합니다. executionId={}", executionId, exception);
        }
        return PlanQuestion.of(PlanQuestionRules.classify(question));
    }

    private record Reply(PlanQuestionType type, List<String> planCodes, boolean unknownPlan) {
    }

    // 요금제 이름을 고르지 않은 목록·개수 질문이면 목록 조건을 한 번 더 묻는다. 읽지 못하면 조건 없이 두어 답변이 낱말로 읽는다
    private PlanQuestion.Mention withListConditions(Long executionId, String question, PlanQuestion.Mention mention,
            List<Plan> plans) {
        if (mention == null || mention.unknownPlan() || !mention.planCodes().isEmpty()) {
            return mention;
        }
        try {
            PlanQuestion.Conditions conditions =
                    parseConditions(llmClient.generate(conditionsRequest(executionId, question, plans)), plans);
            if (conditions == null) {
                log.info("[요금제 목록 조건] 응답을 읽지 못해 질문의 낱말로 조건을 읽습니다. executionId={}", executionId);
            }
            return new PlanQuestion.Mention(List.of(), false, conditions);
        } catch (RuntimeException exception) {
            log.warn("[요금제 목록 조건] 모델 호출에 실패해 질문의 낱말로 조건을 읽습니다. executionId={}", executionId, exception);
            return mention;
        }
    }

    // 추천받을 요금제를 쓸 사람(본인 또는 가족)과 그 사람의 나이대·한 달 데이터 사용량. 모르는 값은 null이다.
    // 나이대와 쓸 사람·사용량은 따로 묻되 동시에 보낸다. 쓸 사람·사용량을 읽지 못하면 null이라 지금처럼 본인 요금제로 보고 묻는다
    public Subscriber subscriber(Long executionId, String question) {
        return subscriber(executionId, question, true);
    }

    // withAge가 false면 나이대는 묻지 않는다(규칙이 질문에서 나이를 이미 읽었을 때). user는 SELF나 OTHER만 받는다
    public Subscriber subscriber(Long executionId, String question, boolean withAge) {
        if (!properties.enabled() || question == null || question.isBlank()) {
            return null;
        }
        CompletableFuture<JsonNode> usage = CompletableFuture.supplyAsync(() -> subscriberReply(executionId, question,
                PlanQuestionPromptTemplates.SUBSCRIBER_USAGE_PROMPT, PlanQuestionPromptTemplates.SUBSCRIBER_USAGE_PROMPT_VERSION,
                SUBSCRIBER_USAGE_SCHEMA));
        JsonNode age = withAge ? subscriberReply(executionId, question, PlanQuestionPromptTemplates.SUBSCRIBER_AGE_PROMPT,
                PlanQuestionPromptTemplates.SUBSCRIBER_AGE_PROMPT_VERSION, SUBSCRIBER_AGE_SCHEMA) : null;
        JsonNode user = usage.join();
        if (user == null || !USERS.contains(user.path("user").asText("")) || !user.path("monthlyDataGb").isNumber()) {
            log.info("[요금제 추천 대상] 쓸 사람을 읽지 못해 본인 요금제로 보고 묻습니다. executionId={}", executionId);
            return null;
        }
        double dataGb = user.path("monthlyDataGb").asDouble();
        Plan.TargetGroup group = age == null ? null : enumValue(Plan.TargetGroup.class, age.path("targetGroup").asText(""));
        return new Subscriber("OTHER".equals(user.path("user").textValue()), group,
                dataGb > 0 && dataGb <= MAX_DATA_GB ? dataGb : null);
    }

    // 스키마의 칸만 있는 JSON 객체. 실패하거나 읽지 못하면 null
    private JsonNode subscriberReply(Long executionId, String question, String prompt, String version, Map<String, Object> schema) {
        try {
            String reply = llmClient.generate(LlmRequest.builder()
                    .executionId(executionId)
                    .taskType(TaskType.ROUTING)
                    .systemPrompt(prompt)
                    .userPrompt(question)
                    .temperature(0.0)
                    .maxTokens(MAX_TOKENS)
                    .promptVersion(version)
                    .responseSchema(schema)
                    .think(false)
                    .build());
            JsonNode root = reply == null || reply.isBlank() ? null : objectMapper.readTree(reply);
            @SuppressWarnings("unchecked")
            Map<String, Object> fields = (Map<String, Object>) schema.get("properties");
            return root != null && root.isObject() && root.size() == fields.size() ? root : null;
        } catch (Exception exception) {
            log.warn("[요금제 추천 대상] 모델 호출에 실패했습니다. promptVersion={}, executionId={}", version, executionId, exception);
            return null;
        }
    }

    /** other: 가족 등 다른 사람이 쓸 요금제. group·monthlyDataGb: 쓸 사람의 나이대와 한 달 데이터 사용량, 모르면 null */
    public record Subscriber(boolean other, Plan.TargetGroup group, Double monthlyDataGb) {
    }

    private static final double MAX_DATA_GB = 10_000;
    private static final Set<String> USERS = Set.of("SELF", "OTHER");
    private static final Map<String, Object> SUBSCRIBER_AGE_SCHEMA = Map.of("type", "object",
            "properties", Map.of("targetGroup", Map.of("type", "string", "enum", List.of("YOUTH", "GENERAL", "SENIOR", "UNKNOWN"))),
            "required", List.of("targetGroup"), "additionalProperties", false);
    private static final Map<String, Object> SUBSCRIBER_USAGE_SCHEMA = Map.of("type", "object",
            "properties", Map.of(
                    "user", Map.of("type", "string", "enum", List.copyOf(USERS)),
                    "monthlyDataGb", Map.of("type", "number")),
            "required", List.of("user", "monthlyDataGb"), "additionalProperties", false);

    private LlmRequest conditionsRequest(Long executionId, String question, List<Plan> plans) {
        return LlmRequest.builder()
                .executionId(executionId)
                .taskType(TaskType.ROUTING)
                .systemPrompt(PlanQuestionPromptTemplates.conditionsSystemPrompt(plans))
                .userPrompt(question)
                .temperature(0.0)
                .maxTokens(MAX_TOKENS)
                .promptVersion(PlanQuestionPromptTemplates.CONDITIONS_PROMPT_VERSION)
                .responseSchema(conditionsSchema(plans))
                .think(false)
                .build();
    }

    static Map<String, Object> conditionsSchema(List<Plan> plans) {
        Map<String, Object> network = Map.of("type", "array", "items", Map.of("type", "string",
                "enum", Arrays.stream(Plan.NetworkType.values()).map(Enum::name).toList()));
        Map<String, Object> group = Map.of("type", "array", "items", Map.of("type", "string",
                "enum", Arrays.stream(Plan.TargetGroup.values()).map(Enum::name).toList()));
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("networks", network);
        properties.put("excludedNetworks", network);
        properties.put("targetGroups", group);
        properties.put("excludedTargetGroups", group);
        properties.put("unlimited", Map.of("type", "string",
                "enum", Arrays.stream(PlanQuestion.Unlimited.values()).map(Enum::name).toList()));
        properties.put("excludedPlans", Map.of("type", "array", "items",
                Map.of("type", "string", "enum", plans.stream().map(Plan::getCode).toList())));
        return Map.of("type", "object", "properties", properties,
                "required", List.copyOf(properties.keySet()), "additionalProperties", false);
    }

    // 여섯 칸이 모두 있고 배열 칸은 허용값만 담은 배열, unlimited는 허용값일 때만 읽는다. 하나라도 어긋나면 null이라
    // 답변이 질문의 낱말로 조건을 읽는다(Bedrock은 응답 스키마를 보내지 않아 형식을 프롬프트로만 요구한다).
    // 판매 중이 아닌 요금제 코드는 버린다
    private PlanQuestion.Conditions parseConditions(String json, List<Plan> plans) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject() || root.size() != CONDITION_FIELDS.size()) {
                return null;
            }
            for (Iterator<String> names = root.fieldNames(); names.hasNext(); ) {
                if (!CONDITION_FIELDS.contains(names.next())) {
                    return null;
                }
            }
            List<Plan.NetworkType> networks = enums(Plan.NetworkType.class, root.path("networks"));
            List<Plan.NetworkType> excludedNetworks = enums(Plan.NetworkType.class, root.path("excludedNetworks"));
            List<Plan.TargetGroup> groups = enums(Plan.TargetGroup.class, root.path("targetGroups"));
            List<Plan.TargetGroup> excludedGroups = enums(Plan.TargetGroup.class, root.path("excludedTargetGroups"));
            PlanQuestion.Unlimited unlimited = root.path("unlimited").isTextual()
                    ? enumValue(PlanQuestion.Unlimited.class, root.path("unlimited").textValue()) : null;
            List<String> excludedPlans = texts(root.path("excludedPlans"));
            if (networks == null || excludedNetworks == null || groups == null || excludedGroups == null || unlimited == null
                    || excludedPlans == null) {
                return null;
            }
            Set<String> active = plans.stream().map(Plan::getCode).collect(Collectors.toSet());
            return new PlanQuestion.Conditions(networks, excludedNetworks, groups, excludedGroups, unlimited,
                    excludedPlans.stream().filter(active::contains).distinct().toList());
        } catch (Exception exception) {
            return null;
        }
    }

    private static final Set<String> CONDITION_FIELDS = Set.of(
            "networks", "excludedNetworks", "targetGroups", "excludedTargetGroups", "unlimited", "excludedPlans");

    // 허용값만 담은 배열이 아니면 null
    private static <E extends Enum<E>> List<E> enums(Class<E> type, JsonNode values) {
        if (!values.isArray()) {
            return null;
        }
        List<E> result = new ArrayList<>();
        for (JsonNode value : values) {
            E parsed = value.isTextual() ? enumValue(type, value.textValue()) : null;
            if (parsed == null) {
                return null;
            }
            if (!result.contains(parsed)) {
                result.add(parsed);
            }
        }
        return result;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    // 문자열만 담은 배열이 아니면 null
    private static List<String> texts(JsonNode values) {
        if (!values.isArray()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) {
            if (!value.isTextual()) {
                return null;
            }
            result.add(value.textValue());
        }
        return result;
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
