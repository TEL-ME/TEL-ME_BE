package com.telme.consult.converter;

import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.exception.ConsultErrorCode;
import com.telme.consult.repository.PendingClarificationFinder.Candidate;
import com.telme.consult.service.FollowupContextService.Context;
import com.telme.consult.service.FollowupSelectionValidator.Selection;
import com.telme.global.common.exception.GeneralException;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 라우팅이 전달한 후속 조건 값을 상담 내부 조건 모델로 변환한다. */
@Component
public class FollowupConditionConverter {

    public Selection convert(
            Context context, long consultRequestId, Map<String, String> extractedConditions) {
        Resolution resolution = resolve(context, consultRequestId, extractedConditions);
        if (!resolution.answersWaitingField()) {
            throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
        }
        return resolution.toSelection();
    }

    public Resolution resolve(
            Context context, long consultRequestId, Map<String, String> extractedConditions) {
        return resolve(context, consultRequestId, extractedConditions, Set.of());
    }

    public Resolution resolve(
            Context context,
            long consultRequestId,
            Map<String, String> extractedConditions,
            Set<String> declinedKeys) {
        Objects.requireNonNull(context, "context");
        if (consultRequestId <= 0) {
            throw new IllegalArgumentException("기존 상담 ID가 필요합니다.");
        }

        Map<String, Condition> updates = conditionUpdates(extractedConditions, declinedKeys);
        var candidate =
                context.candidates().stream()
                        .filter(value -> value.consultRequestId() == consultRequestId)
                        .findFirst()
                        .orElseThrow(() -> new GeneralException(ConsultErrorCode.STATE_CONFLICT));
        return new Resolution(candidate, updates);
    }

    private Map<String, Condition> conditionUpdates(
            Map<String, String> extractedConditions, Set<String> declinedKeys) {
        Objects.requireNonNull(extractedConditions, "extractedConditions");
        Objects.requireNonNull(declinedKeys, "declinedKeys");
        Map<String, Condition> updates = new LinkedHashMap<>();
        extractedConditions.forEach(
                (key, value) -> {
                    if (key == null || key.isBlank()) {
                        throw new IllegalArgumentException("조건 이름이 필요합니다.");
                    }
                    if (value != null && !value.isBlank()) {
                        updates.put(key, Condition.filled(value));
                    }
                });
        declinedKeys.forEach(
                key -> {
                    if (key == null || key.isBlank()) {
                        throw new IllegalArgumentException("거절한 조건 이름이 필요합니다.");
                    }
                    // 같은 키가 값과 거절에 모두 있으면 명시적인 거절을 우선한다.
                    updates.put(key, Condition.declined());
                });
        return Map.copyOf(updates);
    }

    public record Resolution(Candidate candidate, Map<String, Condition> updates) {
        public Resolution {
            Objects.requireNonNull(candidate, "candidate");
            updates = Map.copyOf(Objects.requireNonNull(updates, "updates"));
        }

        public boolean answersWaitingField() {
            return updates.containsKey(candidate.field());
        }

        public Selection toSelection() {
            if (!answersWaitingField()) {
                throw new IllegalStateException("대기 중인 조건에 대한 답변이 아닙니다.");
            }
            return new Selection(
                    candidate.consultRequestId(),
                    candidate.questionMessageId(),
                    candidate.field(),
                    updates);
        }
    }
}
