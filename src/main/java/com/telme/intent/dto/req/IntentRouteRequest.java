package com.telme.intent.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 의도 라우팅 요청 DTO.
 *
 * 팀 코딩 컨벤션 규칙 준수:
 *   - 위치: {domain}/dto/req
 *   - 명명: {Domain}{Action}Request -> IntentRouteRequest
 *   - Java record 사용
 */
public record IntentRouteRequest(
    @NotNull(message = "메시지 ID는 필수입니다.")
    Long messageId,

    @NotBlank(message = "질문 내용은 비어 있을 수 없습니다.")
    String content
) {}
