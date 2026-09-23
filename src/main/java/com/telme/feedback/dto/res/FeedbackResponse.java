package com.telme.feedback.dto.res;

import com.telme.feedback.dto.FeedbackModels.Rating;
import com.telme.feedback.dto.FeedbackModels.Reason;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;

import java.time.Instant;

@Builder
public record FeedbackResponse(
        Long id,
        Long messageId,
        Rating rating,
        @Schema(description = "DISLIKE 사유 코드: WRONG_INFO(정보 오류), NOT_RELATED(관련 없음), "
                + "HARD_TO_READ(읽기 어려움). LIKE이면 null")
        Reason reason,
        String comment,
        Instant createdAt,
        Instant updatedAt) {}
