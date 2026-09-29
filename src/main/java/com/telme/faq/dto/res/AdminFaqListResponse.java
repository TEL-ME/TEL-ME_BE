package com.telme.faq.dto.res;

import java.util.List;

public record AdminFaqListResponse(
        List<AdminFaqListItemResponse> faqs,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
