package com.telme.consult.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.dto.LlmConditionPayload;
import com.telme.consult.dto.MissingCondition;
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/** 검색된 FAQ만 보고 되물을 조건을 뽑는다. 뽑지 못하면 되묻지 않고 답변으로 넘어간다. */
@Slf4j
public class ConditionExtractor {

    private static final int MAX_TOKENS = 500;
    private static final int MAX_CONDITIONS = 2;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ConditionExtractor(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = Objects.requireNonNull(llmClient);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public List<MissingCondition> extract(Long executionId, String userQuery, List<FaqSearchResponse> sources) {
        if (userQuery == null || userQuery.isBlank() || sources == null || sources.isEmpty()) {
            return List.of();
        }
        LlmConditionPayload payload = generate(executionId, userQuery, sources);
        if (payload == null) {
            return List.of();
        }
        List<MissingCondition> conditions = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (var candidate : payload.conditions()) {
            // 같은 조건을 두 번 물으면 안 되고, 중복이 개수 제한을 먼저 채우면 다른 조건이 밀려난다
            toCondition(candidate, sources)
                    .filter(condition -> keys.add(condition.key()))
                    .ifPresent(conditions::add);
            if (conditions.size() == MAX_CONDITIONS) {
                break;
            }
        }
        return List.copyOf(conditions);
    }

    private LlmConditionPayload generate(Long executionId, String userQuery, List<FaqSearchResponse> sources) {
        LlmRequest request = LlmRequest.builder()
                .executionId(executionId)
                .taskType(TaskType.CONDITION_EXTRACT)
                .systemPrompt(ConditionPromptTemplates.CONDITION_SYSTEM_PROMPT)
                .userPrompt(ConditionPromptTemplates.buildUserPrompt(userQuery, sources))
                .format(ResponseFormat.JSON)
                .temperature(0.0)
                .maxTokens(MAX_TOKENS)
                .promptVersion(ConditionPromptTemplates.PROMPT_VERSION)
                .build();
        try {
            String json = llmClient.generate(request);
            if (json == null || json.isBlank()) {
                return null;
            }
            return objectMapper.readValue(json, LlmConditionPayload.class);
        } catch (JsonProcessingException exception) {
            log.info("[조건 뽑기] 응답을 읽지 못해 되묻지 않습니다. executionId={}", executionId);
            return null;
        } catch (RuntimeException exception) {
            // 되묻기를 못 해도 답변은 나가야 한다
            log.warn("[조건 뽑기] 모델 호출에 실패해 되묻지 않습니다. executionId={}", executionId, exception);
            return null;
        }
    }

    private java.util.Optional<MissingCondition> toCondition(
            LlmConditionPayload.ConditionPayload candidate, List<FaqSearchResponse> sources) {
        ConditionGrounding.Grounded grounded =
                ConditionGrounding.groundedEvidence(candidate.evidence(), sources);
        if (grounded == null) {
            log.info("[조건 뽑기] 근거에 없는 조건을 버립니다. key={}", candidate.key());
            return java.util.Optional.empty();
        }
        try {
            // 선택지는 근거로 인정된 FAQ 안에서만 찾는다. 다른 FAQ에 있는 값은 이 질문의 선택지가 아니다
            return java.util.Optional.of(new MissingCondition(
                    candidate.key(), candidate.question(),
                    ConditionGrounding.groundedOptions(candidate.options(), grounded.source()),
                    grounded.sentence()));
        } catch (IllegalArgumentException exception) {
            log.info("[조건 뽑기] 쓸 수 없는 조건을 건너뜁니다. key={}", candidate.key());
            return java.util.Optional.empty();
        }
    }
}
