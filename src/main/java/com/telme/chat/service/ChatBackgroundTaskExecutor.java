package com.telme.chat.service;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
class ChatBackgroundTaskExecutor {

    static final String THREAD_NAME_PREFIX = "chat-background-";

    private final ThreadPoolTaskExecutor executor;

    ChatBackgroundTaskExecutor() {
        this(createExecutor());
    }

    ChatBackgroundTaskExecutor(ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    void execute(Runnable task) {
        executor.execute(task);
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
