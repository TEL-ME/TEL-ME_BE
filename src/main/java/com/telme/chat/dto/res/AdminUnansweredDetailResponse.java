package com.telme.chat.dto.res;

import java.time.Instant;
import java.util.List;

public record AdminUnansweredDetailResponse(
        Long messageId,
        Long sessionId,
        String type,
        // 그 답변을 요청한 사용자 메시지. 되묻기 뒤에는 원래 질문이 아니라 조건 답변이 들어간다
        String question,
        // 근거를 못 찾은 경우는 안내 문구가, 답을 끝내지 못한 경우는 빈 값이 들어 있다
        String answer,
        Instant createdAt,
        List<AdminUnansweredSourceResponse> sources
) {
}
