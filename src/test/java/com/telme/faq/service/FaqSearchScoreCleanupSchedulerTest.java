package com.telme.faq.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.telme.faq.config.SearchProperties;
import com.telme.faq.repository.FaqSearchScoreRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FaqSearchScoreCleanupSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-10-07T00:00:00Z");
    
    @Test
    @DisplayName("보존 기간 이전에 생긴 기록을 지운다")
    void 보존_기간_이전을_지운다() {
        FaqSearchScoreRepository repository = mock(FaqSearchScoreRepository.class);
        SearchProperties properties = new SearchProperties(0.72, new SearchProperties.DualVector(false, 0.88),
                                      new SearchProperties.ScoreRecording(true, Duration.ofDays(90)));
        FaqSearchScoreCleanupScheduler scheduler =
                new FaqSearchScoreCleanupScheduler(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        
        scheduler.deleteExpired();
        
        verify(repository).deleteCreatedBefore(Instant.parse("2026-07-09T00:00:00Z"));
    }
}
