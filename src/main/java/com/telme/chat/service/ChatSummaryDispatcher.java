package com.telme.chat.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
class ChatSummaryDispatcher {

    static final String THREAD_NAME_PREFIX = "chat-summary-";

    private final ChatSummaryService chatSummaryService;
    private final ThreadPoolTaskExecutor executor;

    @Autowired
    ChatSummaryDispatcher(ChatSummaryService chatSummaryService) {
        this(chatSummaryService, createExecutor());
    }

    ChatSummaryDispatcher(ChatSummaryService chatSummaryService, ThreadPoolTaskExecutor executor) {
        this.chatSummaryService = chatSummaryService;
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(ChatSummaryRequested request) {
        try {
            executor.execute(() -> summarize(request));
        } catch (TaskRejectedException exception) {
            log.warn("상담 요약 요청을 대기열에 추가하지 못함: executionId={}, sessionId={}",
                    request.executionId(), request.sessionId(), exception);
        }
    }

    private void summarize(ChatSummaryRequested request) {
        try {
            chatSummaryService.summarizeIfNeeded(request);
        } catch (RuntimeException exception) {
            log.warn("상담 요약 생성 실패, 다음 완료 요청에서 재시도: executionId={}, sessionId={}",
                    request.executionId(), request.sessionId(), exception);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    private static ThreadPoolTaskExecutor createExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
