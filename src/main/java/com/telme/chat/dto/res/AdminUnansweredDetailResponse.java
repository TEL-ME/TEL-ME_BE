package com.telme.chat.dto.res;

import java.time.Instant;
import java.util.List;

public record AdminUnansweredDetailResponse(
        Long messageId,
        Long sessionId,
        String type,
        String question,
        // 근거를 못 찾은 경우는 안내 문구가, 답을 끝내지 못한 경우는 빈 값이 들어 있다
        String answer,
        Instant createdAt,
        List<AdminUnansweredSourceResponse> sources
) {
}
