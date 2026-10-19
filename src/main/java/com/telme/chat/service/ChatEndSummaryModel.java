package com.telme.chat.service;

import com.telme.llm.dto.req.LlmRequest;
import java.time.Instant;

@FunctionalInterface
public interface ChatEndSummaryModel {
    String generate(LlmRequest request, Instant deadline);
}
