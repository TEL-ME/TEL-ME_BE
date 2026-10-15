package com.telme.speech.service;

import com.telme.chat.service.ChatActor;
import com.telme.global.common.exception.GeneralException;
import com.telme.speech.exception.SpeechErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

// 사용자별 분당 요청 수와 서비스 전체 하루 사용량(글자 수·초 등)을 함께 센다. 서버 메모리 기준이라 재시작하면 초기화된다
final class UsageQuota {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final int PRUNE_THRESHOLD = 10_000;

    private final Clock clock;
    private final int requestsPerMinute;
    private final long dailyLimit;
    private final Map<String, Window> windows = new HashMap<>();
    private LocalDate day;
    private long usedToday;

    UsageQuota(Clock clock, int requestsPerMinute, long dailyLimit) {
        this.clock = clock;
        this.requestsPerMinute = requestsPerMinute;
        this.dailyLimit = dailyLimit;
    }

    synchronized void acquire(ChatActor actor, long amount) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, KOREA);
        if (!today.equals(day)) {
            day = today;
            usedToday = 0;
        }
        if (usedToday + amount > dailyLimit) {
            throw new GeneralException(SpeechErrorCode.DAILY_LIMIT_EXCEEDED);
        }

        String key = actor.isMember() ? "user:" + actor.userId() : "guest:" + actor.guestId();
        Window window = windows.get(key);
        if (window == null || !now.isBefore(window.startedAt().plus(WINDOW))) {
            window = new Window(now, 0);
        }
        if (window.count() >= requestsPerMinute) {
            throw new GeneralException(SpeechErrorCode.TOO_MANY_REQUESTS);
        }

        if (windows.size() >= PRUNE_THRESHOLD) {
            windows.values().removeIf(w -> !now.isBefore(w.startedAt().plus(WINDOW)));
        }
        windows.put(key, new Window(window.startedAt(), window.count() + 1));
        usedToday += amount;
    }

    private record Window(Instant startedAt, int count) {
    }
}
