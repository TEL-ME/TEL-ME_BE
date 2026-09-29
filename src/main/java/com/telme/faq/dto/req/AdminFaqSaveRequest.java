package com.telme.faq.dto.req;

import com.telme.faq.entity.Faq;
import com.telme.faq.entity.FaqCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 등록과 수정이 같은 항목을 받는다. status는 기본값을 두지 않는다 —
// 등록에서 생략하면 ACTIVE지만 수정에서 생략하면 기존 상태를 그대로 둬야 하기 때문
public record AdminFaqSaveRequest(
        @NotNull FaqCategory category,
        @NotBlank @Size(max = 500) String question,
        @NotBlank @Size(max = 5000) String answer,
        @Size(max = 50) String policyRef,
        Faq.Status status
) {
    public AdminFaqSaveRequest {
        question = question == null ? null : question.strip();
        answer = answer == null ? null : answer.strip();
        policyRef = policyRef == null || policyRef.isBlank() ? null : policyRef.strip();
    }
}
