package com.telme.chat.service;

import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.service.LlmStreamHandler;

// LLM 토큰을 구독자에게 그대로 넘기고, 구독자가 떠났으면 예외로 생성을 멈추게 하는 핸들러.
// 완료·실패는 알리지 않는다. AnswerGenerator.generate()처럼 끝날 때까지 기다렸다가 실패를 예외로 다시 던지는
// 호출과 함께 써야 하고, 호출한 쪽은 LlmStreamCancelledException을 삼키지 말고 취소로 처리해야 한다.
public class ChatStreamRelay implements LlmStreamHandler {

    private final Long executionId;
    private final ChatStreamPublisher streamPublisher;
    private final StringBuilder relayed = new StringBuilder();

    public ChatStreamRelay(Long executionId, ChatStreamPublisher streamPublisher) {
        this.executionId = executionId;
        this.streamPublisher = streamPublisher;
    }

    @Override
    public void onToken(String token) {
        if (!streamPublisher.publishToken(executionId, token)) {
            throw new LlmStreamCancelledException();
        }
        relayed.append(token);
    }

    @Override
    public void onRetry(int attempt, Throwable cause) {
        streamPublisher.publishRetrying(executionId);
    }

    @Override
    public void onComplete() {
    }

    @Override
    public void onError(Throwable error) {
    }

    // 구독자에게 넘긴 문구(구독 전이라 버려진 것 포함). 저장한 답변과 비교해 아직 넘기지 않은 나머지만 이어 보낼 때 쓴다
    public String relayed() {
        return relayed.toString();
    }
}
