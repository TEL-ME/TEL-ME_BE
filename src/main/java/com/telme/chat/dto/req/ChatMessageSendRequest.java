package com.telme.chat.dto.req;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageSendRequest(
        @NotBlank(message = "메시지 내용을 입력해 주세요.")
        String content
) {
}
