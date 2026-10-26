package com.telme.chat.dto.req;

import jakarta.validation.constraints.Size;

public record ChatSessionCreateRequest(
        @Size(max = 100, message = "채팅 세션 제목은 100자 이하여야 합니다.")
        String title
) {
}
