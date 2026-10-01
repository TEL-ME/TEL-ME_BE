package com.telme.feedback.dto.res;

import java.util.List;

public record AdminFeedbackListResponse(
        List<AdminFeedbackListItemResponse> feedbacks,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
