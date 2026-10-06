package com.telme.faq.dto.req;

import com.telme.faq.exception.FaqErrorCode;
import com.telme.global.common.exception.GeneralException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

// vector가 null이면 search.dual-vector.enabled 설정을 따른다
public record FaqSearchRequest(
        @NotBlank @Size(max = 500) String query,
        @Positive @Max(10) Integer topK,
        FaqSearchVector vector
) {
    private static final int DEFAULT_TOP_K = 3;

    // 채팅·상담 경로용. 벡터를 고르지 않고 설정을 따른다
    public FaqSearchRequest(String query, Integer topK) {
        this(query, topK, null);
    }

    public FaqSearchRequest {
        if (topK == null) {
            topK = DEFAULT_TOP_K;
        } else if (topK <= 0) {
            throw new GeneralException(FaqErrorCode.INVALID_TOP_K);
        }
    }
}
