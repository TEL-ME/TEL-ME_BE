package com.telme.chat.translation;

import com.telme.chat.exception.TranslationErrorCode;
import com.telme.chat.service.ChatActor;
import com.telme.global.common.exception.GeneralException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

//번역은 채팅 답변과 같은 로컬 모델을 써서 몰리면 답변이 느려진다. 사용자별 분당 요청 수를 막는다.
//서버 메모리 기준이라 재시작하면 초기화된다(SpeechUsageLimiter와 같은 방식)
@Component
public class TranslationUsageLimiter {

    static final int REQUESTS_PER_MINUTE = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int PRUNE_THRESHOLD = 10_000;
    
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();
    
    public TranslationUsageLimiter(Clock clock) {
        this.clock = clock;
    }

    public synchronized void acquire(ChatActor actor) {
        Instant now = clock.instant();
        String key = actor.isMember() ? "user:" + actor.userId() : "guest:" + actor.guestId();
        Window window = windows.get(key);
        if (window == null || !now.isBefore(window.startedAt.plus(WINDOW))) {
            window = new Window(now, 0);
        }
        if (window.count() >= REQUESTS_PER_MINUTE) {
            throw new GeneralException(TranslationErrorCode.TOO_MANY_REQUESTS);
        }
        if (windows.size() >= PRUNE_THRESHOLD) {
            windows.values().removeIf(w -> !now.isBefore(w.startedAt.plus(WINDOW)));
        }
        windows.put(key, new Window(window.startedAt(), window.count() + 1));
    }

    private record Window(Instant startedAt, int count) {
    }
}
