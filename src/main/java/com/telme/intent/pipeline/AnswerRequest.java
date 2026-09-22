package com.telme.intent.pipeline;

import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.List;
import lombok.Builder;

@Builder
public record AnswerRequest(
        Long executionId,
        String userQuery,
        List<FaqSearchResponse> searchResults
) {}
