package com.telme.llm.service;

import java.util.function.Supplier;

// 지금 스레드의 LLM 호출이 주 모델(Bedrock)이 실패해 대체 모델로 다시 부른 것인지 표시한다
// FallbackLlmClient가 대체 호출을 하는 동안에만 켜진다
// 조건 추출의 파싱 재시도처럼 대체 클라이언트를 직접 부르는 경로는 표시되지 않아 구분할 수 있다
// 대체 호출은 같은 스레드에서 끝까지 실행되므로(스트리밍도 blocking) ThreadLocal로 충분하다
final class LlmFallbackScope {

    private static final ThreadLocal<Boolean> ACTIVE = new ThreadLocal<>();

    private LlmFallbackScope() {
    }

    static boolean active() {
        return Boolean.TRUE.equals(ACTIVE.get());
    }

    static <T> T run(Supplier<T> call) {
        Boolean previous = ACTIVE.get();
        ACTIVE.set(Boolean.TRUE);
        try {
            return call.get();
        } finally {
            restore(previous);
        }
    }

    static void run(Runnable call) {
        run(() -> {
            call.run();
            return null;
        });
    }

    // 스레드 풀에서 값이 남지 않게 원래 없었으면 지운다
    private static void restore(Boolean previous) {
        if (previous == null) {
            ACTIVE.remove();
        } else {
            ACTIVE.set(previous);
        }
    }
}
