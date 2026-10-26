package com.telme.speech.service;

import com.telme.chat.service.ChatActor;
import com.telme.speech.config.SpeechSttProperties;
import com.telme.speech.entity.SpeechDailyUsage;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

// 게스트는 쿠키만 지우면 새로 발급돼 사용자별 한도를 피할 수 있으므로, 서비스 전체 하루 인식 시간이 실제 비용 상한이다
@Component
public class TranscriptionUsageLimiter {

    // Transcribe는 요청마다 최소 15초를 과금하므로 짧은 녹음도 15초로 센다
    private static final long MIN_BILLED_SECONDS = 15;

    private final UsageQuota quota;

    public TranscriptionUsageLimiter(Clock clock, SpeechSttProperties properties,
            SpeechDailyUsageService dailyUsageService) {
        this.quota = new UsageQuota(clock, properties.requestsPerMinute(), properties.dailyDurationLimit().toSeconds(),
                SpeechDailyUsage.Kind.STT_SECONDS, dailyUsageService);
    }

    // TTS와 같이 Transcribe 호출 전에 차감하고 실패해도 돌려주지 않는다. 시간 초과는 이미 과금됐을 수 있고, 인식 실패(SPEECH422-0)도 과금된다
    public void acquire(ChatActor actor, Duration audioLength) {
        long seconds = (audioLength.toMillis() + 999) / 1000;
        quota.acquire(actor, Math.max(MIN_BILLED_SECONDS, seconds));
    }
}
