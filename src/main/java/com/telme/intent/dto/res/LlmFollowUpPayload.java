package com.telme.intent.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;

// 상담 도메인의 Condition과 엮이지 않도록 상태 enum을 따로 둔다
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmFollowUpPayload(
    ResponseType responseType,
    List<ConditionPayload> conditions
) {
    public enum ResponseType {
        CONDITION_RESPONSE,
        DEFERRED,
        NEW_QUESTION
    }

    public LlmFollowUpPayload(List<ConditionPayload> conditions) {
        this(null, conditions);
    }

    public LlmFollowUpPayload {
        if (conditions == null) conditions = Collections.emptyList();
        if (responseType == null) {
            responseType = conditions.isEmpty()
                ? ResponseType.DEFERRED
                : ResponseType.CONDITION_RESPONSE;
        }
    }

    public enum Status {
        FILLED, DECLINED
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConditionPayload(
        String key,
        Status status,
        String value
    ) {}
}
