package com.telme.chat.dto.res;

import java.util.List;

public record AdminUnansweredListResponse(
        List<AdminUnansweredListItemResponse> messages,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
