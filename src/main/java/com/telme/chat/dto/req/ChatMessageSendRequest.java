package com.telme.chat.dto.req;

import com.telme.chat.service.ChatCoordinates;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ChatMessageSendRequest(
        @NotBlank(message = "메시지 내용을 입력해 주세요.")
        @Size(max = 2000, message = "메시지 내용은 2,000자 이하여야 합니다.")
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

    @AssertTrue(message = "위도와 경도를 함께 입력하고 올바른 좌표 범위를 사용해 주세요.")
    public boolean isCoordinatesValid() {
        return ChatCoordinates.isValid(latitude, longitude);
    }

    @Override
    public String toString() {
        return "ChatMessageSendRequest[content=REDACTED, coordinates=REDACTED]";
    }
}
