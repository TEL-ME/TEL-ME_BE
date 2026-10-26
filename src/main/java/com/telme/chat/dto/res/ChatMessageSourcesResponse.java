package com.telme.chat.dto.res;

import java.util.List;

public record ChatMessageSourcesResponse(
        Long messageId,
        List<ChatMessageSourceResponse> sources
) {

    public ChatMessageSourcesResponse {
        sources = List.copyOf(sources);
    }
}
