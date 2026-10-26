package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.chat.config.ChatExecutionProperties;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.notification.dto.NotificationType;
import com.telme.notification.service.NotificationService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ChatExecutionTimeoutSchedulerTest {

    private static final Instant STARTED_BEFORE = Instant.parse("2026-09-18T00:00:00Z");

    @Mock
    private ChatExecutionRepository chatExecutionRepository;

    @Mock
    private ChatExecutionService chatExecutionService;

    @Mock
    private ChatEmitterRegistry emitterRegistry;

    @Mock
    private NotificationService notificationService;

    private ChatExecutionTimeoutScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ChatExecutionTimeoutScheduler(
                chatExecutionRepository, chatExecutionService, new ChatExecutionProperties(Duration.ofMinutes(5)),
                emitterRegistry, new StaticListableBeanFactory(Map.of("notificationService", notificationService))
                        .getBeanProvider(NotificationService.class));
    }

    @Test
    void timedOutExecutionSavesFailedNotificationOnlyWhenNoScreenReceivedIt() {
        stubPage(0L, List.of(1L, 2L));
        when(emitterRegistry.fail(eq(1L), any())).thenReturn(true);
        when(emitterRegistry.fail(eq(2L), any())).thenReturn(false);

        scheduler.timeOutExecutionsStartedBefore(STARTED_BEFORE);

        verify(notificationService, never()).saveUndelivered(eq(1L), any());
        verify(notificationService).saveUndelivered(2L, NotificationType.ANSWER_FAILED);
    }

    @Test
    void notificationFailureDoesNotStopTimingOutOtherExecutions() {
        stubPage(0L, List.of(1L, 2L));
        doThrow(new IllegalStateException("db down")).when(notificationService).saveUndelivered(eq(1L), any());

        int timedOut = scheduler.timeOutExecutionsStartedBefore(STARTED_BEFORE);

        assertThat(timedOut).isEqualTo(2);
        verify(notificationService).saveUndelivered(2L, NotificationType.ANSWER_FAILED);
    }

    @Test
    void continuesAfterLastIdUntilPageIsNotFull() {
        stubPage(0L, ids(1, 100));
        stubPage(100L, ids(101, 130));

        int timedOut = scheduler.timeOutExecutionsStartedBefore(STARTED_BEFORE);

        assertThat(timedOut).isEqualTo(130);
        verify(chatExecutionService, times(130)).fail(anyLong(), any(ChatFailure.class));
        verify(chatExecutionRepository, times(2))
                .findExecutionIdsStartedBefore(any(), any(), anyLong(), any(Pageable.class));
    }

    @Test
    void movesPastExecutionThatCannotBeTimedOut() {
        stubPage(0L, ids(1, 100));
        stubPage(100L, List.of());
        doThrow(new GeneralException(ChatErrorCode.EXECUTION_NOT_RUNNING))
                .when(chatExecutionService).fail(eq(1L), any(ChatFailure.class));

        int timedOut = scheduler.timeOutExecutionsStartedBefore(STARTED_BEFORE);

        assertThat(timedOut).isEqualTo(99);
        verify(chatExecutionRepository).findExecutionIdsStartedBefore(
                eq(ChatExecution.Status.RUNNING), eq(STARTED_BEFORE), eq(100L), any(Pageable.class));
    }

    @Test
    void stopsAtMaxBatchesAndLeavesRestForNextRun() {
        when(chatExecutionRepository.findExecutionIdsStartedBefore(
                eq(ChatExecution.Status.RUNNING), eq(STARTED_BEFORE), anyLong(), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    long afterId = invocation.getArgument(2);
                    return ids(afterId + 1, afterId + ChatExecutionTimeoutScheduler.BATCH_SIZE);
                });

        int timedOut = scheduler.timeOutExecutionsStartedBefore(STARTED_BEFORE);

        assertThat(timedOut).isEqualTo(ChatExecutionTimeoutScheduler.MAX_BATCHES * ChatExecutionTimeoutScheduler.BATCH_SIZE);
        verify(chatExecutionRepository, times(ChatExecutionTimeoutScheduler.MAX_BATCHES))
                .findExecutionIdsStartedBefore(any(), any(), anyLong(), any(Pageable.class));
    }

    private void stubPage(long afterId, List<Long> executionIds) {
        when(chatExecutionRepository.findExecutionIdsStartedBefore(
                eq(ChatExecution.Status.RUNNING), eq(STARTED_BEFORE), eq(afterId), any(Pageable.class)))
                .thenReturn(executionIds);
    }

    private static List<Long> ids(long from, long to) {
        return LongStream.rangeClosed(from, to).boxed().toList();
    }
}
