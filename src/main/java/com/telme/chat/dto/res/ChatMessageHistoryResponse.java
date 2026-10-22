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
        Long runningExecutionId,
        @Schema(description = "답변 생성이 실패해 다시 시도할 수 있는 실행 ID. 새로고침 후 다시 시도 버튼을 그릴 때 사용하며, "
                + "마지막 실행이 재시도 가능 실패(LLM502-4)이고 아직 다시 시도하지 않았을 때만 값이 있고 아니면 null")
        Long retryableExecutionId
) {
}
