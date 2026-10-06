package com.telme.llm.service;

public interface LlmStreamHandler {

    void onToken(String token);

    void onComplete();

    void onError(Throwable error);

    // 내용 전송 없이 생성 진행을 알린다. 버퍼링 중에도 호출자의 취소를 확인할 수 있다.
    default void onProgress() {
    }

    // 첫 토큰 전 실패로 재시도할 때 호출한다. 기본 구현이 있어 기존 구현체는 수정하지 않아도 된다
    default void onRetry(int attempt, Throwable cause) {
    }
}
