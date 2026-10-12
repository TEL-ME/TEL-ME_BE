package com.telme.chat.guard;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** 감지 이력만 정리한다. 채팅 메시지·현재 제한 상태에는 삭제를 전파하지 않는다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "chat.input-guard.cleanup-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class InputGuardRetention {
    private final JdbcInputGuardStore store;
    private final InputGuardProperties properties;
    private final Clock clock;

    @Scheduled(cron = "${chat.input-guard.cleanup-cron:0 0 3 * * *}", zone = "UTC")
    @Transactional
    public void purgeExpiredEvents() {
        store.purgeBefore(clock.instant().minus(properties.retention()));
    }
}
