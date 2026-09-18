package com.telme.chat.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.global.common.exception.GeneralException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
class ChatProcessingDispatcher {

    static final String NOT_CONNECTED = "AI_NOT_CONNECTED";
    static final String PROCESSING_ERROR = "AI_PROCESSING_ERROR";
    static final String DISPATCH_REJECTED = "AI_DISPATCH_REJECTED";
    static final String THREAD_NAME_PREFIX = "chat-processing-";

    private final ObjectProvider<ChatProcessingPort> chatProcessingPort;
    private final ChatExecutionService chatExecutionService;
    private final TransactionTemplate newTransaction;
    private final ThreadPoolTaskExecutor executor;

    @Autowired
    ChatProcessingDispatcher(
            ObjectProvider<ChatProcessingPort> chatProcessingPort,
            ChatExecutionService chatExecutionService,
            PlatformTransactionManager transactionManager
    ) {
        this(chatProcessingPort, chatExecutionService, transactionManager, createExecutor());
    }

    ChatProcessingDispatcher(
            ObjectProvider<ChatProcessingPort> chatProcessingPort,
            ChatExecutionService chatExecutionService,
            PlatformTransactionManager transactionManager,
            ThreadPoolTaskExecutor executor
    ) {
        this.chatProcessingPort = chatProcessingPort;
        this.chatExecutionService = chatExecutionService;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(ChatProcessingCommand command) {
        try {
            executor.execute(() -> process(command));
        } catch (TaskRejectedException exception) {
            log.error("AI 처리 요청 대기열이 가득 참: executionId={}", command.executionId(), exception);
            failQuietly(command, DISPATCH_REJECTED);
        }
    }

    private void process(ChatProcessingCommand command) {
        try {
            ChatProcessingPort port = chatProcessingPort.getIfAvailable();
            if (port == null) {
                failQuietly(command, NOT_CONNECTED);
                return;
            }
            port.request(command);
        } catch (RuntimeException exception) {
            log.error("AI 처리 중 오류: executionId={}", command.executionId(), exception);
            failQuietly(command, PROCESSING_ERROR);
        }
    }

    private void failQuietly(ChatProcessingCommand command, String errorCode) {
        try {
            newTransaction.executeWithoutResult(status -> chatExecutionService.fail(
                    command.executionId(), new ChatFailure(ChatMessage.Status.FAILED, errorCode)));
        } catch (GeneralException exception) {
            log.info("AI 처리 실패 기록 건너뜀: executionId={}, code={}",
                    command.executionId(), exception.getErrorCode().getCode());
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    private static ThreadPoolTaskExecutor createExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
