package com.telme.chat.dto.res;

import java.util.List;

public record AdminChatMonitoringListResponse(
        List<AdminChatMonitoringListItemResponse> items,
        // 다음 페이지 커서(없으면 null)
        Long nextBeforeId
) {
}
