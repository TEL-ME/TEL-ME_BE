package com.telme.consult.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Objects;

/** 조건 뽑기 모델의 응답. 어느 칸이든 비어 올 수 있어 읽는 쪽에서 막지 않는다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmConditionPayload(List<ConditionPayload> conditions) {

    public LlmConditionPayload {
        conditions = conditions == null ? List.of() : conditions.stream().filter(Objects::nonNull).toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ConditionPayload(String key, String question, List<String> options, String evidence) {
        public ConditionPayload {
            options = options == null ? List.of() : options.stream().filter(Objects::nonNull).toList();
        }
    }
}
