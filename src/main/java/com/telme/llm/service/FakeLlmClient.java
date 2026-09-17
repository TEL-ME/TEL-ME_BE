package com.telme.llm.service;

import org.springframework.stereotype.Component;

import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;

@Component
public class FakeLlmClient implements LlmClient {

    private static final String FAKE_TEXT = "[FAKE] 테스트용 응답입니다.";
    private static final String FAKE_JSON = "{}";

    @Override
    public String generate(LlmRequest request) {
        if (request.format() == ResponseFormat.JSON) {
            return FAKE_JSON;
        }
        return FAKE_TEXT;
    }

    @Override
    public void stream(LlmRequest request, LlmStreamHandler handler) {
        try {
            for (String token : FAKE_TEXT.split("(?<= )")) {
                handler.onToken(token);
            }
        } catch (RuntimeException e) {
            handler.onError(e);
            return;
        }
        // try 안에 두면 onComplete 실패 시 onError까지 호출됨
        handler.onComplete();
    }
}
