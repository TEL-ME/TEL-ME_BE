package com.telme.speech.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telme.speech.config.SpeechSttProperties;
import com.telme.speech.config.SpeechTtsProperties;
import com.telme.speech.entity.SpeechDailyUsage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.springframework.util.unit.DataSize;

final class SpeechTestSupport {

    private SpeechTestSupport() {
    }

    static SpeechTtsProperties properties(int requestsPerMinute, int dailyCharacterLimit, long cacheMaxBytes) {
        return new SpeechTtsProperties("ap-northeast-2", "Seoyeon", "neural", 3000, Duration.ofSeconds(10),
                requestsPerMinute, dailyCharacterLimit, DataSize.ofBytes(cacheMaxBytes), Duration.ofHours(1));
    }

    static SpeechSttProperties sttProperties(int requestsPerMinute, Duration dailyDurationLimit) {
        return sttProperties(requestsPerMinute, dailyDurationLimit, null);
    }

    static SpeechSttProperties sttProperties(int requestsPerMinute, Duration dailyDurationLimit, String vocabularyName) {
        return new SpeechSttProperties("ap-northeast-2", "ko-KR", Duration.ofSeconds(30), Duration.ofMillis(300),
                requestsPerMinute, dailyDurationLimit, vocabularyName);
    }

    // DB의 조건부 upsert와 같은 규칙(합이 한도 이하일 때만 차감)으로 세는 메모리 저장소. 단위 테스트는 DB 없이 리미터 동작만 본다
    static SpeechDailyUsageService inMemoryDailyUsage() {
        Map<String, Long> used = new HashMap<>();
        SpeechDailyUsageService service = mock(SpeechDailyUsageService.class);
        when(service.tryCharge(any(LocalDate.class), any(SpeechDailyUsage.Kind.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0) + ":" + invocation.getArgument(1);
                    long amount = invocation.getArgument(2);
                    long limit = invocation.getArgument(3);
                    synchronized (used) {
                        long next = used.getOrDefault(key, 0L) + amount;
                        if (next > limit) {
                            return false;
                        }
                        used.put(key, next);
                        return true;
                    }
                });
        return service;
    }

    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
