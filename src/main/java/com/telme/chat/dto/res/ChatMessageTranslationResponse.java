package com.telme.chat.dto.res;

public record ChatMessageTranslationResponse(
        Long messageId,
        String language,
        String content
        ) {
}
