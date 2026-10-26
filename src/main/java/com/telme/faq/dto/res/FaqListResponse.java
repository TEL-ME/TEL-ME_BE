package com.telme.faq.dto.res;

import java.util.List;

public record FaqListResponse(
        List<FaqResponse> faqs,
        int page,
        int size,
        long totalElements,
        int totalPages
        ) {

}
