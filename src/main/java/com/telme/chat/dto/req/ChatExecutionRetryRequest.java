package com.telme.chat.dto.req;

import com.telme.chat.service.ChatCoordinates;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;

@Schema(description = "다시 시도 요청. 원래 질문에 위치를 보냈다면 같은 위치를 다시 보냅니다. 없으면 본문 없이 호출해도 됩니다.")
public record ChatExecutionRetryRequest(Double latitude, Double longitude) {

    @AssertTrue(message = "위도와 경도를 함께 입력하고 올바른 좌표 범위를 사용해 주세요.")
    public boolean isCoordinatesValid() {
        return ChatCoordinates.isValid(latitude, longitude);
    }

    @Override
    public String toString() {
        return "ChatExecutionRetryRequest[coordinates=REDACTED]";
    }
}
