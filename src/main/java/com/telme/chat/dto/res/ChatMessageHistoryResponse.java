package com.telme.chat.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ChatMessageHistoryResponse(
        List<ChatMessageHistoryItemResponse> messages,
        @Schema(description = "더 오래된 메시지를 조회할 때 사용할 sequenceNo")
        Integer nextBeforeSequenceNo,
        @Schema(description = "현재 응답보다 오래된 메시지가 더 있는지 여부")
        boolean hasOlderMessages,
        @Schema(description = "답변을 생성 중인 실행 ID. 새로고침 후 스트림을 다시 구독할 때 사용하며, 없으면 null")
        Long runningExecutionId
) {
}
