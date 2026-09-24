package com.telme.consult.converter;

import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.ConditionStatus;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 상담 내부 조건 중 확인된 값만 검색·답변 모듈에 전달한다. */
@Component
public class ConfirmedConditionConverter {
    public Map<String, String> convert(Map<String, Condition> conditions) {
        Objects.requireNonNull(conditions, "conditions");
        return conditions.entrySet().stream()
                .filter(entry -> entry.getValue().status() == ConditionStatus.FILLED)
                .collect(
                        Collectors.toUnmodifiableMap(
                                Map.Entry::getKey, entry -> entry.getValue().value()));
    }
}
