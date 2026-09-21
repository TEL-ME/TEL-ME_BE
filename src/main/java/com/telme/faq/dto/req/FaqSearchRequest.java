package com.telme.faq.dto.req;

import com.telme.faq.exception.FaqErrorCode;
import com.telme.global.common.exception.GeneralException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FaqSearchRequest(
        @NotBlank @Size(max = 500) String query,
        @Positive @Max(10) Integer topK
) {
    private static final int DEFAULT_TOP_K = 3;

    public FaqSearchRequest {
        if (topK == null) {
            topK = DEFAULT_TOP_K;
        } else if (topK <= 0) {
            throw new GeneralException(FaqErrorCode.INVALID_TOP_K);
        }
    }
}
