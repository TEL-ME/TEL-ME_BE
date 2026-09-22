package com.telme.intent.pipeline;

import java.util.List;
import lombok.Builder;

@Builder
public record AnswerResult(
        String answer,
        List<AnswerSource> sources
) {}
