package com.telme.chat.dto.req;

import com.telme.chat.service.ChatCoordinates;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ChatMessageSendRequest(
        @NotBlank(message = "{chat.content.required}")
        @Size(max = 2000, message = "{chat.content.size}")
        String content,
        Double latitude,
        Double longitude,
        UUID requestId
) {
    public ChatMessageSendRequest(String content) {
        this(content, null, null, null);
    }

    public ChatMessageSendRequest(String content, Double latitude, Double longitude) {
        this(content, latitude, longitude, null);
    }

    @AssertTrue(message = "{chat.coordinates.invalid}")
    public boolean isCoordinatesValid() {
        return ChatCoordinates.isValid(latitude, longitude);
    }

    @Override
    public String toString() {
        return "ChatMessageSendRequest[content=REDACTED, coordinates=REDACTED]";
    }
}
