package com.telme.llm.exception;

// SSE 쪽에서 스트리밍을 멈추려 할 때 onToken에서 던진다. 실패가 아니라 중단으로 기록한다
public class LlmStreamCancelledException extends RuntimeException {

    public LlmStreamCancelledException() {
        super("LLM 스트리밍이 호출한 쪽에서 중단되었습니다.");
    }
}
