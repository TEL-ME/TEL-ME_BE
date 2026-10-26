package com.telme.feedback.converter;

import com.telme.feedback.dto.FeedbackModels.Feedback;
import com.telme.feedback.dto.FeedbackModels.Input;
import com.telme.feedback.dto.req.FeedbackSaveRequest;
import com.telme.feedback.dto.res.FeedbackResponse;
import com.telme.feedback.exception.FeedbackErrorCode;
import com.telme.global.common.exception.GeneralException;

import org.springframework.stereotype.Component;

@Component
public class FeedbackConverter {
    public Input toInput(FeedbackSaveRequest request) {
        try {
            return new Input(request.rating(), request.reason(), request.comment());
        } catch (IllegalArgumentException exception) {
            throw new GeneralException(FeedbackErrorCode.INVALID_REQUEST);
        }
    }

    public FeedbackResponse toResponse(Feedback feedback) {
        return FeedbackResponse.builder()
                .id(feedback.id())
                .messageId(feedback.messageId())
                .rating(feedback.input().rating())
                .reason(feedback.input().reason())
                .comment(feedback.input().comment())
                .createdAt(feedback.createdAt())
                .updatedAt(feedback.updatedAt())
                .build();
    }
}
