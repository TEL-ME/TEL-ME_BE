package com.telme.chat.service;

import java.util.concurrent.TimeUnit;

// nanoTime은 같은 JVM 안에서 경과 시간을 잴 때만 의미가 있다. 저장하거나 다른 서버 값과 비교하지 않는다.
public record ChatResponseStopwatch(long startedNanos) {

    public static ChatResponseStopwatch start() {
        return new ChatResponseStopwatch(System.nanoTime());
    }

    // INTEGER 컬럼을 넘거나 거꾸로 된 값은 잘라서 저장하지 않고 측정 불가로 남긴다.
    Integer elapsedMillis(long nowNanos) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(nowNanos - startedNanos);
        return elapsedMs < 0 || elapsedMs > Integer.MAX_VALUE ? null : (int) elapsedMs;
    }
}
