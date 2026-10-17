package com.telme.chat.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatSessionTitleUpdateRequest(
        @NotBlank(message = "{chat.title.required}")
        @Size(max = 100, message = "{chat.title.size}")
        String title
) {
}
