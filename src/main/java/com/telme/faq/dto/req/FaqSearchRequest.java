package com.telme.faq.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record FaqSearchRequest(
        @NotBlank String query,
        @Positive int topK
) {
}
