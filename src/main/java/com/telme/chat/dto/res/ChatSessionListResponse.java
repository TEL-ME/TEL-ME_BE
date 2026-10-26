package com.telme.chat.dto.res;

import java.util.List;

public record ChatSessionListResponse(
        List<ChatSessionListItemResponse> sessions,
        String nextCursor,
        boolean hasNext
) {
}
