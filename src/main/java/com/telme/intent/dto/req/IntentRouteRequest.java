package com.telme.intent.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IntentRouteRequest(
    @NotNull(message = "메시지 ID는 필수입니다.")
    Long messageId,

    @NotBlank(message = "질문 내용은 비어 있을 수 없습니다.")
    String content
) {}
