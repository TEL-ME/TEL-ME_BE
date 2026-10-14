package com.telme.chat.dto.req;

import jakarta.validation.constraints.Size;

public record ChatSessionCreateRequest(
        @Size(max = 100, message = "{chat.title.size}")
        String title
) {
}
