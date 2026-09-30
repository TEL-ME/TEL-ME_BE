package com.telme.feedback.dto.req;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// handled를 false로 주면 처리 표시를 되돌린다. 잘못 누른 경우를 되살릴 방법이 없으면
// 관리자가 목록에서 그 건을 다시 찾을 수 없다
public record AdminFeedbackHandleRequest(
        @NotNull Boolean handled,
        @Size(max = 500) String note
) {
    public AdminFeedbackHandleRequest {
        note = note == null || note.isBlank() ? null : note.strip();
    }
}
