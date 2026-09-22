package com.telme.intent.pipeline;

import com.telme.llm.service.LlmStreamHandler;

public interface AnswerGenerator {

    AnswerResult generate(AnswerRequest request, LlmStreamHandler handler);
}
