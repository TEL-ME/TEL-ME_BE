package com.telme.faq.dto.req;

import jakarta.validation.constraints.NotNull;

// 수정 API는 질문·답변이 필수라 상태만 바꿀 때 본문을 통째로 다시 보내야 한다
public record AdminFaqStatusRequest(
        @NotNull AdminFaqStatusChange status,
        Integer lockVersion
) {
}
