package com.telme.chat.service;

import com.telme.chat.config.ChatEndSummaryProperties;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
class ChatSessionEndSummaryDispatcher {
    private final ChatSessionEndSummaryService service;
    private final ChatSessionEndSummaryStore store;
    private final ChatEndSummaryProperties properties;
    private final ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    private final Set<Long> submitted = ConcurrentHashMap.newKeySet();

    @PostConstruct
    void initialize() {
        executor.setThreadNamePrefix("chat-end-summary-");
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(20);
        executor.initialize();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(ChatSessionEndSummaryService.Requested request) {
        if (properties.enabled() && properties.dispatchEnabled()) {
            submit(request.sessionId());
        }
    }

    @Scheduled(initialDelayString = "${chat.end-summary.poll-delay:PT5S}",
            fixedDelayString = "${chat.end-summary.poll-delay:PT5S}")
    public void recover() {
        if (properties.enabled() && properties.schedulerEnabled()) {
            for (Long id : store.candidates()) {
                submit(id);
            }
        }
    }

    private void submit(long id) {
        if (!submitted.add(id)) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    service.process(id);
                } catch (RuntimeException exception) {
                    log.warn("종료 요약 작업을 다시 점검해야 함: sessionId={}", id);
                } finally {
                    submitted.remove(id);
                }
            });
        } catch (TaskRejectedException exception) {
            submitted.remove(id);
            log.info("종료 요약 대기열 포화, 다음 점검에서 재접수: sessionId={}", id);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
        submitted.clear();
    }
}
