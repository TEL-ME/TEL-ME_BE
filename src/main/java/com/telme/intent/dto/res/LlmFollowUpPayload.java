package com.telme.intent.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;

// 상담 도메인의 Condition과 엮이지 않도록 상태 enum을 따로 둔다
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmFollowUpPayload(
    List<ConditionPayload> conditions
) {
    public LlmFollowUpPayload {
        if (conditions == null) conditions = Collections.emptyList();
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
