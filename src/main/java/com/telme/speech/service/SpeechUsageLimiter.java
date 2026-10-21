package com.telme.speech.service;

import com.telme.chat.service.ChatActor;
import com.telme.speech.config.SpeechTtsProperties;
import com.telme.speech.entity.SpeechDailyUsage;
import java.time.Clock;
import org.springframework.stereotype.Component;

// Polly 비용은 팀 공용 크레딧에서 나가므로 사용자별 분당 요청 수와 서비스 전체 하루 글자 수(DB)를 막는다
@Component
public class SpeechUsageLimiter {

    private final UsageQuota quota;

    public SpeechUsageLimiter(Clock clock, SpeechTtsProperties properties, SpeechDailyUsageService dailyUsageService) {
        this.quota = new UsageQuota(clock, properties.requestsPerMinute(), properties.dailyCharacterLimit(),
                SpeechDailyUsage.Kind.TTS_CHARACTERS, dailyUsageService);
    }

    // Polly 호출 전에 차감하고 실패해도 돌려주지 않는다. 시간 초과는 Polly에서 이미 과금됐을 수 있고, 실패를 반복하는 요청도 막아야 해서다
    public void acquire(ChatActor actor, int characters) {
        quota.acquire(actor, characters);
    }
}
