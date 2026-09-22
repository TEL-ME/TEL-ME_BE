package com.telme.intent.pipeline;

import com.telme.llm.service.LlmStreamHandler;
import java.util.Collections;

// RAG 답변 생성기 연결 전까지 쓰는 임시 구현
public class DummyAnswerGenerator implements AnswerGenerator {

    @Override
    public AnswerResult generate(AnswerRequest request, LlmStreamHandler handler) {
        String answer = "안녕하세요! 문의하신 질문에 대해 확인된 정보입니다.";
        if (handler != null) {
            handler.onToken(answer);
            handler.onComplete();
        }
        return AnswerResult.builder()
                .answer(answer)
                .sources(Collections.emptyList())
                .build();
    }
}
