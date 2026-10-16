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
    private final ChatResponseTimer responseTimer;
    private final ThreadPoolTaskExecutor executor;

    @Autowired
    ChatProcessingDispatcher(
            ObjectProvider<ChatProcessingPort> chatProcessingPort,
            ChatExecutionService chatExecutionService,
            ChatResponseTimer responseTimer,
            PlatformTransactionManager transactionManager
    ) {
        this(chatProcessingPort, chatExecutionService, responseTimer, transactionManager, createExecutor());
    }

    ChatProcessingDispatcher(
            ObjectProvider<ChatProcessingPort> chatProcessingPort,
            ChatExecutionService chatExecutionService,
            ChatResponseTimer responseTimer,
            PlatformTransactionManager transactionManager,
            ThreadPoolTaskExecutor executor
    ) {
        this.chatProcessingPort = chatProcessingPort;
        this.chatExecutionService = chatExecutionService;
        this.responseTimer = responseTimer;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.executor = executor;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void dispatch(ChatProcessingCommand command) {
        // 대기열에서 기다린 시간도 처리 시간에 넣기 위해 작업을 넘기기 전에 등록한다.
        ChatResponseTimer.Registration registration = responseTimer.register(command);
        if (registration == null) {
            // 이미 처리 중인 실행이다. 기존 작업의 측정값·상태를 건드리지 않고 넘긴다
            log.warn("이미 처리 중인 실행의 처리 요청을 건너뜀: executionId={}", command.executionId());
            return;
        }
        boolean submitted = false;
        try {
            executor.execute(() -> process(command, registration));
            submitted = true;
        } catch (TaskRejectedException exception) {
            log.error("AI 처리 요청 대기열이 가득 참: executionId={}", command.executionId(), exception);
            failQuietly(command, DISPATCH_REJECTED);
        } finally {
            if (!submitted) {
                responseTimer.release(command.executionId(), registration);
            }
        }
    }

    private void process(ChatProcessingCommand command, ChatResponseTimer.Registration registration) {
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
        } finally {
            responseTimer.release(command.executionId(), registration);
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
