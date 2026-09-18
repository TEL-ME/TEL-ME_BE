package com.telme.feedback.dto.req;

import com.telme.feedback.dto.FeedbackModels.Rating;
import com.telme.feedback.dto.FeedbackModels.Reason;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Builder;

@Builder
public record FeedbackSaveRequest(
        @NotNull Rating rating, Reason reason, @Size(max = 1000) String comment) {}
