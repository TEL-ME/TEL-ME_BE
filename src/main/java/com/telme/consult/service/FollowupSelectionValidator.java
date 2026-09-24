package com.telme.consult.service;

import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.ConditionStatus;
import com.telme.consult.exception.ConsultErrorCode;
import com.telme.global.common.exception.GeneralException;

import java.util.Map;
import java.util.Objects;

/** 분석 쪽에서 선택한 상담과 추출 조건을 확인한다. 자연어는 여기서 분석하지 않는다. */
public final class FollowupSelectionValidator {
    public record Selection(
            long consultRequestId,
            long questionMessageId,
            String field,
            Map<String, Condition> updates) {
        public Selection {
            if (consultRequestId <= 0
                    || questionMessageId <= 0
                    || field == null
                    || field.isBlank()) {
                throw new IllegalArgumentException("후속 답변의 상담·질문·조건이 필요합니다.");
            }
            updates = Map.copyOf(Objects.requireNonNull(updates, "updates"));
        }
    }

    public record ResolvedFollowup(
            long consultRequestId,
            long userMessageId,
            String answeredField,
            Map<String, Condition> updates) {
        public ResolvedFollowup {
            updates = Map.copyOf(updates);
        }
    }

    public ResolvedFollowup validate(FollowupContextService.Context context, Selection selection) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(selection, "selection");
        boolean matches =
                context.candidates().stream()
                        .anyMatch(
                                candidate ->
                                        candidate.consultRequestId() == selection.consultRequestId()
                                                && candidate.questionMessageId()
                                                        == selection.questionMessageId()
                                                && candidate.field().equals(selection.field()));
        if (!matches) {
            throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
        }
        var condition = selection.updates().get(selection.field());
        if (condition == null || condition.status() == ConditionStatus.PENDING) {
            throw new IllegalArgumentException("후속 답변은 조건 값을 채우거나 제공 거절로 전달해야 합니다.");
        }
        return new ResolvedFollowup(
                selection.consultRequestId(),
                context.userMessageId(),
                selection.field(),
                selection.updates());
    }
}
