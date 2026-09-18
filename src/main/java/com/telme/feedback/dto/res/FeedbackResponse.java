package com.telme.feedback.dto.res;

import com.telme.feedback.dto.FeedbackModels.Rating;
import com.telme.feedback.dto.FeedbackModels.Reason;

import lombok.Builder;

import java.time.Instant;

@Builder
public record FeedbackResponse(
        Long id,
        Long messageId,
        Rating rating,
        Reason reason,
        String comment,
        Instant createdAt,
        Instant updatedAt) {}
