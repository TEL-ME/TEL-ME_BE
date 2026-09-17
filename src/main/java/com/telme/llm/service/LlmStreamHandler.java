package com.telme.llm.service;

public interface LlmStreamHandler {

    void onToken(String token);

    void onComplete();

    void onError(Throwable error);
}
