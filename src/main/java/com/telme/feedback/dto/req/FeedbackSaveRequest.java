package com.telme.feedback.dto.req;

import com.telme.feedback.dto.FeedbackModels.Rating;
import com.telme.feedback.dto.FeedbackModels.Reason;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Builder;

@Builder
public record FeedbackSaveRequest(
        @Schema(description = "평가: LIKE 또는 DISLIKE", example = "DISLIKE")
        @NotNull Rating rating,
        @Schema(description = "DISLIKE일 때 필수: WRONG_INFO(정보 오류), NOT_RELATED(관련 없음), "
                + "HARD_TO_READ(읽기 어려움). LIKE일 때는 보내지 않습니다.", example = "WRONG_INFO")
        Reason reason,
        @Schema(description = "DISLIKE일 때만 입력할 수 있는 의견. 앞뒤 공백을 제거하며 최대 1000자입니다.")
        @Size(max = 1000) String comment) {}
