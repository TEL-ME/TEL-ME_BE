package com.telme.speech.service;

import com.telme.chat.service.ChatActor;
import com.telme.global.common.exception.GeneralException;
import com.telme.speech.entity.SpeechDailyUsage;
import com.telme.speech.exception.SpeechErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

// 사용자별 분당 요청 수는 서버 메모리로, 서비스 전체 하루 사용량(글자 수·초 등)은 DB로 센다.
// 분당 수까지 DB로 옮기면 요청마다 DB 쓰기가 생기는데, 재시작으로 잃는 건 최대 1분치라 메모리에 둔다(서버가 여러 대면 대수만큼 느슨해진다)
final class UsageQuota {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final int PRUNE_THRESHOLD = 10_000;

    private final Clock clock;
    private final int requestsPerMinute;
    private final long dailyLimit;
    private final SpeechDailyUsage.Kind kind;
    private final SpeechDailyUsageService dailyUsageService;
    private final Map<String, Window> windows = new HashMap<>();

    UsageQuota(Clock clock, int requestsPerMinute, long dailyLimit, SpeechDailyUsage.Kind kind,
            SpeechDailyUsageService dailyUsageService) {
        this.clock = clock;
        this.requestsPerMinute = requestsPerMinute;
        this.dailyLimit = dailyLimit;
        this.kind = kind;
        this.dailyUsageService = dailyUsageService;
    }

    // 분당 횟수를 먼저 확인해 막힌 요청은 DB를 쓰지 않는다. DB 차감 중 잠금을 잡지 않도록 횟수를 먼저 잡아 두고, 막히거나 실패하면 되돌린다
    void acquire(ChatActor actor, long amount) {
        Instant now = clock.instant();
        String key = actor.isMember() ? "user:" + actor.userId() : "guest:" + actor.guestId();
        Instant windowStart = reserveRequest(key, now);

        boolean charged = false;
        try {
            charged = dailyUsageService.tryCharge(LocalDate.ofInstant(now, KOREA), kind, amount, dailyLimit);
        } catch (DataAccessException | TransactionException e) {
            // 사용량을 확인하지 못하면 통과시키지 않는다. 한도가 비용 방어 장치라서 DB가 죽었을 때 열어 두면 안 된다.
            // DB 연결 자체가 안 될 때는 DataAccessException이 아니라 트랜잭션 시작 실패(TransactionException)가 던져진다
            throw new GeneralException(SpeechErrorCode.USAGE_UNAVAILABLE);
        } finally {
            if (!charged) {
                releaseRequest(key, windowStart);
            }
        }
        if (!charged) {
            throw new GeneralException(SpeechErrorCode.DAILY_LIMIT_EXCEEDED);
        }
    }

    private synchronized Instant reserveRequest(String key, Instant now) {
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
        return window.startedAt();
    }

    // 그 사이 1분이 지나 창이 바뀌었으면 이미 새 창이므로 건드리지 않는다
    private synchronized void releaseRequest(String key, Instant windowStart) {
        Window window = windows.get(key);
        if (window != null && window.startedAt().equals(windowStart) && window.count() > 0) {
            windows.put(key, new Window(windowStart, window.count() - 1));
        }
    }

    private record Window(Instant startedAt, int count) {
    }
}
