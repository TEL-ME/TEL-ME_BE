package com.telme.rag.service;

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
class MessageSourceDispatcher {

    static final String THREAD_NAME_PREFIX = "message-source-";

    private final MessageSourceWriter messageSourceWriter;
    private final ThreadPoolTaskExecutor executor;

    @Autowired
    MessageSourceDispatcher(MessageSourceWriter messageSourceWriter) {
        this(messageSourceWriter, createExecutor());
    }

    MessageSourceDispatcher(MessageSourceWriter messageSourceWriter, ThreadPoolTaskExecutor executor) {
        this.messageSourceWriter = messageSourceWriter;
        this.executor = executor;
    }

    // 커밋 전에 저장하면 답변 메시지가 아직 안 보여 FK 위반이 난다
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(AnswerSourcesReady event) {
        if (event.sources().isEmpty()) {
            return;
        }
        try {
            executor.execute(() -> write(event));
        } catch (TaskRejectedException exception) {
            log.warn("[MessageSourceDispatcher] 근거 저장 대기열이 가득 참 messageId={}",
                    event.answerMessageId(), exception);
        }
    }

    private void write(AnswerSourcesReady event) {
        try {
            messageSourceWriter.write(event.answerMessageId(), event.sources());
        } catch (RuntimeException exception) {
            // 근거가 안 남는 것보다 답변이 안 나가는 쪽이 더 나쁘다
            log.warn("[MessageSourceDispatcher] 답변 근거 저장 실패 messageId={}",
                    event.answerMessageId(), exception);
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
