package com.telme.speech.service;

import com.telme.speech.config.SpeechSttProperties;
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

    static SpeechSttProperties sttProperties(int requestsPerMinute, Duration dailyDurationLimit) {
        return sttProperties(requestsPerMinute, dailyDurationLimit, null);
    }

    static SpeechSttProperties sttProperties(int requestsPerMinute, Duration dailyDurationLimit, String vocabularyName) {
        return new SpeechSttProperties("ap-northeast-2", "ko-KR", Duration.ofSeconds(30), Duration.ofMillis(300),
                requestsPerMinute, dailyDurationLimit, vocabularyName);
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
