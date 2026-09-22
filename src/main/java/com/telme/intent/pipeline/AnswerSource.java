package com.telme.intent.pipeline;

import lombok.Builder;

@Builder
public record AnswerSource(
        Long faqId,
        String question,
        Double score
) {}
