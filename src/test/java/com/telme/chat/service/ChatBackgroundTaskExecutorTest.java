package com.telme.chat.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ChatBackgroundTaskExecutorTest {

    @Test
    void runsTasksOneAtATime() throws InterruptedException {
        ChatBackgroundTaskExecutor executor = new ChatBackgroundTaskExecutor();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        AtomicInteger activeTasks = new AtomicInteger();
        AtomicInteger maximumActiveTasks = new AtomicInteger();

        try {
            executor.execute(() -> {
                trackStart(activeTasks, maximumActiveTasks);
                firstStarted.countDown();
                await(releaseFirst);
                activeTasks.decrementAndGet();
                completed.countDown();
            });
            assertThat(firstStarted.await(5, SECONDS)).isTrue();

            executor.execute(() -> {
                trackStart(activeTasks, maximumActiveTasks);
                secondStarted.countDown();
                activeTasks.decrementAndGet();
                completed.countDown();
            });

            assertThat(secondStarted.await(200, MILLISECONDS)).isFalse();
            releaseFirst.countDown();
            assertThat(completed.await(5, SECONDS)).isTrue();
            assertThat(maximumActiveTasks).hasValue(1);
        } finally {
            releaseFirst.countDown();
            executor.shutdown();
        }
    }

    private void trackStart(AtomicInteger activeTasks, AtomicInteger maximumActiveTasks) {
        int active = activeTasks.incrementAndGet();
        maximumActiveTasks.accumulateAndGet(active, Math::max);
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
