package com.telme.llm.dto.res;

import java.util.List;

public record AdminLlmErrorListResponse(
        List<AdminLlmErrorListItemResponse> errors,
        int page,
        int size,
        long totalElements,
        int totalPages
        ) {
}
