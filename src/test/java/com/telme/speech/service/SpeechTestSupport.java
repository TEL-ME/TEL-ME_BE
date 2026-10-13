package com.telme.speech.service;

import com.telme.speech.config.SpeechTtsProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.util.unit.DataSize;

final class SpeechTestSupport {

    private SpeechTestSupport() {
    }

    static SpeechTtsProperties properties(int requestsPerMinute, int dailyCharacterLimit, long cacheMaxBytes) {
        return new SpeechTtsProperties("ap-northeast-2", "Seoyeon", "neural", 3000, Duration.ofSeconds(10),
                requestsPerMinute, dailyCharacterLimit, DataSize.ofBytes(cacheMaxBytes), Duration.ofHours(1));
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
