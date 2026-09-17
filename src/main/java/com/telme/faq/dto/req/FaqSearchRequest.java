package com.telme.faq.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record FaqSearchRequest(
        @NotBlank String query,
        @Positive Integer topK
) {
    private static final int DEFAULT_TOP_K = 3;

    public FaqSearchRequest {
        if (topK == null) {
            topK = DEFAULT_TOP_K;
        } else if (topK <= 0) {
            throw new IllegalArgumentException("topK는 0보다 커야 합니다: " + topK);
        }
    }
}
