package com.telme.chat.service;

import com.telme.chat.config.ChatProcessingProperties;
import com.telme.chat.entity.ChatMessage;
import com.telme.global.common.exception.GeneralException;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
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
    static final String SERVER_SHUTDOWN = "AI_SERVER_SHUTDOWN";
    static final String THREAD_NAME_PREFIX = "chat-processing-";

    private final ObjectProvider<ChatProcessingPort> chatProcessingPort;
    private final ChatExecutionService chatExecutionService;
    private final TransactionTemplate newTransaction;
    private final ChatResponseTimer responseTimer;
    private final ThreadPoolTaskExecutor executor;
    private final Duration shutdownWait;
    private final Set<ProcessingTask> running = ConcurrentHashMap.newKeySet();

    @Autowired
    ChatProcessingDispatcher(
            ObjectProvider<ChatProcessingPort> chatProcessingPort,
            ChatExecutionService chatExecutionService,
            ChatResponseTimer responseTimer,
            PlatformTransactionManager transactionManager,
            ChatProcessingProperties properties
    ) {
        this(chatProcessingPort, chatExecutionService, responseTimer, transactionManager, createExecutor(properties),
                properties.shutdownWait());
    }

    ChatProcessingDispatcher(
            ObjectProvider<ChatProcessingPort> chatProcessingPort,
            ChatExecutionService chatExecutionService,
            ChatResponseTimer responseTimer,
            PlatformTransactionManager transactionManager,
            ThreadPoolTaskExecutor executor
    ) {
        this(chatProcessingPort, chatExecutionService, responseTimer, transactionManager, executor,
                ChatProcessingProperties.DEFAULT_SHUTDOWN_WAIT);
    }

    ChatProcessingDispatcher(
            ObjectProvider<ChatProcessingPort> chatProcessingPort,
            ChatExecutionService chatExecutionService,
            ChatResponseTimer responseTimer,
            PlatformTransactionManager transactionManager,
            ThreadPoolTaskExecutor executor,
            Duration shutdownWait
    ) {
        this.chatProcessingPort = chatProcessingPort;
        this.chatExecutionService = chatExecutionService;
        this.responseTimer = responseTimer;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.executor = executor;
        this.shutdownWait = shutdownWait;
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
            executor.execute(new ProcessingTask(command, registration));
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

    // 재배포(SIGTERM) 때 실행기를 shutdownNow()로 닫으면 진행 중인 턴이 인터럽트되고 대기열의 턴은 버려진다.
    // 그러면 그 실행들이 RUNNING으로 남아 타임아웃 정리(기본 5분)가 올 때까지 고객은 응답을 기다린다.
    // 그래서 새 작업을 막고, 시작 전 턴은 바로 실패로 기록하고, 진행 중인 턴은 대기 시간까지 끝나기를 기다린 뒤 남은 것도 실패로 기록한다
    @PreDestroy
    void shutdown() {
        ThreadPoolExecutor pool = executor.getThreadPoolExecutor();
        pool.shutdown();

        List<Runnable> notStarted = new ArrayList<>();
        pool.getQueue().drainTo(notStarted);
        notStarted.forEach(task -> abandonNotStarted(task));

        if (!awaitTermination(pool)) {
            log.warn("AI 처리 종료 대기 시간 초과, 남은 {}건을 실패로 기록: wait={}", running.size(), shutdownWait);
            // 인터럽트보다 먼저 기록해야 인터럽트로 생기는 일반 처리 오류(PROCESSING_ERROR)가 아니라 재시작 때문임을 남긴다
            running.forEach(task -> failOnShutdown(task.command()));
            pool.shutdownNow();
        }
    }

    private boolean awaitTermination(ThreadPoolExecutor pool) {
        try {
            return pool.awaitTermination(shutdownWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void abandonNotStarted(Runnable task) {
        if (task instanceof ProcessingTask processingTask) {
            failOnShutdown(processingTask.command());
            responseTimer.release(processingTask.command().executionId(), processingTask.registration());
        }
    }

    // 종료 중에는 DB 오류가 나도 나머지 턴 정리와 실행기 종료를 이어가야 하므로 예외를 밖으로 내보내지 않는다.
    // 기록하지 못한 실행은 타임아웃 정리가 뒤에서 닫는다
    private void failOnShutdown(ChatProcessingCommand command) {
        try {
            failQuietly(command, SERVER_SHUTDOWN);
        } catch (RuntimeException exception) {
            log.error("종료 중 AI 처리 실패 기록 실패: executionId={}", command.executionId(), exception);
        }
    }

    // 종료 때 어떤 턴이 진행 중인지 알려면 작업을 실행 정보와 함께 들고 있어야 해서 람다 대신 둔다
    private final class ProcessingTask implements Runnable {

        private final ChatProcessingCommand command;
        private final ChatResponseTimer.Registration registration;

        private ProcessingTask(ChatProcessingCommand command, ChatResponseTimer.Registration registration) {
            this.command = command;
            this.registration = registration;
        }

        ChatProcessingCommand command() {
            return command;
        }

        ChatResponseTimer.Registration registration() {
            return registration;
        }

        @Override
        public void run() {
            running.add(this);
            try {
                process(command, registration);
            } finally {
                running.remove(this);
            }
        }
    }

    // 동시 처리 수만큼 스레드를 고정해 두고, 넘치는 턴은 대기열에서 기다린다(chat.processing)
    static ThreadPoolTaskExecutor createExecutor(ChatProcessingProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setCorePoolSize(properties.threads());
        executor.setMaxPoolSize(properties.threads());
        executor.setQueueCapacity(properties.queueCapacity());
        executor.initialize();
        return executor;
    }
}
