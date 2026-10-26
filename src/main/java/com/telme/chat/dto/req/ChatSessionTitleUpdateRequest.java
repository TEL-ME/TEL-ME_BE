package com.telme.chat.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatSessionTitleUpdateRequest(
        @NotBlank(message = "채팅 세션 제목을 입력해 주세요.")
        @Size(max = 100, message = "채팅 세션 제목은 100자 이하여야 합니다.")
        String title
) {
}
