package com.telme.feedback.dto.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

// handled를 false로 주면 처리 표시를 되돌린다. 잘못 누른 경우를 되살릴 방법이 없으면
// 관리자가 목록에서 그 건을 다시 찾을 수 없다
public record AdminFeedbackHandleRequest(
        @NotNull Boolean handled,
        @Size(max = 500) String note,
        // 조회에서 받은 값을 그대로 돌려보내면 그 사이 사용자가 고친 경우를 막는다. 생략하면 검사하지 않는다
        Instant updatedAt
) {
    public AdminFeedbackHandleRequest {
        note = note == null || note.isBlank() ? null : note.strip();
    }
}
