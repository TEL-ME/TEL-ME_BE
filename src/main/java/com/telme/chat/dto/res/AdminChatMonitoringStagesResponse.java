package com.telme.chat.dto.res;

import lombok.Builder;

@Builder
public record AdminChatMonitoringStagesResponse(
        AdminChatMonitoringStageResponse inputCheck,
        AdminChatMonitoringStageResponse routing,
        AdminChatMonitoringStageResponse search,
        AdminChatMonitoringStageResponse judgment,
        AdminChatMonitoringStageResponse generation,
        AdminChatMonitoringStageResponse outputCheck,
        AdminChatMonitoringStageResponse result
) {
}
