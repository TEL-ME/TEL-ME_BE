package com.telme.faq.service;

import com.telme.faq.config.SearchProperties;
import com.telme.faq.repository.FaqSearchScoreRepository;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "search.score-recording.enabled", havingValue = "true", matchIfMissing = true)
public class FaqSearchScoreCleanupScheduler {

    private final FaqSearchScoreRepository repository;
    private final SearchProperties searchProperties;
    private final Clock clock;
    
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    public void deleteExpired() {
        int deleted = repository.deleteCreatedBefore(
                clock.instant().minus(searchProperties.scoreRecording().retention()));
        if (deleted > 0) {
            log.info("[FaqSearchScoreCleanup] 보존 기간이 지난 검색 점수 기록 삭제: count={}", deleted);
        }
    }
}
