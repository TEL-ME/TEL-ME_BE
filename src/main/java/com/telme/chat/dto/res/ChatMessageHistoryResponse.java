package com.telme.chat.dto.res;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ChatMessageHistoryResponse(
        List<ChatMessageHistoryItemResponse> messages,
        @Schema(description = "더 오래된 메시지를 조회할 때 사용할 sequenceNo")
        Integer nextBeforeSequenceNo,
        @Schema(description = "현재 응답보다 오래된 메시지가 더 있는지 여부")
        boolean hasOlderMessages
) {
}
