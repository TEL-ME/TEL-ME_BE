package com.telme.chat.service;

// 답변 처리 과정을 구독 중인 클라이언트(SSE)로 내보내는 출구.
// 처리 파이프라인이 SSE 연결 관리를 모르도록 분리했고, 구현체는 전송 실패를 삼키고 예외를 던지지 않아야 한다.
// DB 상태 반영이 끝난 뒤 호출되므로 여기서 예외가 나면 이미 완료된 실행을 실패로 덮어쓰게 된다.
public interface ChatStreamPublisher {

    void publishStarted(Long executionId, ChatExecutionState state);

    // false는 구독자가 떠났다는 뜻이다. 호출한 쪽은 생성을 멈춘다.
    boolean publishToken(Long executionId, String token);

    void publishRetrying(Long executionId);

    void publishCompleted(Long executionId, ChatExecutionState state);

    void publishFailed(Long executionId, ChatFailure failure);
}
