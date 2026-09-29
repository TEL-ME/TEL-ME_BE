package com.telme.faq.dto.req;

import com.telme.faq.entity.Faq;
import com.telme.faq.entity.FaqCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 등록과 수정이 같은 항목을 받는다. 등록은 status를 생략하면 ACTIVE로 들어간다
public record AdminFaqSaveRequest(
        @NotNull FaqCategory category,
        @NotBlank @Size(max = 500) String question,
        @NotBlank @Size(max = 5000) String answer,
        @Size(max = 50) String policyRef,
        Faq.Status status
) {
    public AdminFaqSaveRequest {
        status = status == null ? Faq.Status.ACTIVE : status;
        question = question == null ? null : question.strip();
        answer = answer == null ? null : answer.strip();
        policyRef = policyRef == null || policyRef.isBlank() ? null : policyRef.strip();
    }
}
