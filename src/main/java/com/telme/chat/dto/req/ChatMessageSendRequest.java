package com.telme.chat.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatMessageSendRequest(
        @NotBlank(message = "메시지 내용을 입력해 주세요.")
        @Size(max = 2000, message = "메시지 내용은 2,000자 이하여야 합니다.")
        String content
) {
}
