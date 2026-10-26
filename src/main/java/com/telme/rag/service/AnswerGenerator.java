package com.telme.rag.service;

import com.telme.llm.service.LlmStreamHandler;
import com.telme.rag.dto.req.AnswerRequest;
import com.telme.rag.dto.res.AnswerResult;

public interface AnswerGenerator {

    // 완료될 때까지 blocking. 토큰은 handler로, 최종 답변과 근거는 반환값으로 전달
    AnswerResult generate(AnswerRequest request, LlmStreamHandler handler);
}
