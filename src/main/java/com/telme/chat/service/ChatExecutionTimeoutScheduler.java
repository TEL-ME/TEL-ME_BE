package com.telme.chat.service;

import com.telme.chat.config.ChatExecutionProperties;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.notification.dto.NotificationType;
import com.telme.notification.service.NotificationService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chat.execution.timeout-scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class ChatExecutionTimeoutScheduler {

    static final String ERROR_CODE = "EXECUTION_TIMEOUT";
    static final int BATCH_SIZE = 100;
    static final int MAX_BATCHES = 50;

    private final ChatExecutionRepository chatExecutionRepository;
    private final ChatExecutionService chatExecutionService;
    private final ChatExecutionProperties chatExecutionProperties;
    private final ChatEmitterRegistry emitterRegistry;
    private final ObjectProvider<NotificationService> notifications;

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void timeOutStaleExecutions() {
        timeOutExecutionsStartedBefore(Instant.now().minus(chatExecutionProperties.runningTimeout()));
    }

    int timeOutExecutionsStartedBefore(Instant startedBefore) {
        int timedOut = 0;
        long afterId = 0;

        for (int batch = 0; batch < MAX_BATCHES; batch++) {
            List<Long> executionIds = chatExecutionRepository.findExecutionIdsStartedBefore(
                    ChatExecution.Status.RUNNING, startedBefore, afterId, PageRequest.of(0, BATCH_SIZE));

            for (Long executionId : executionIds) {
                try {
                    ChatFailure failure = new ChatFailure(ChatMessage.Status.TIMEOUT, ERROR_CODE);
                    chatExecutionService.fail(executionId, failure);
                    timedOut++;
                    notifyTimedOut(executionId, failure);
                } catch (GeneralException exception) {
                    log.info("채팅 실행 타임아웃 처리 건너뜀: executionId={}, code={}",
                            executionId, exception.getErrorCode().getCode());
                }
            }

            if (executionIds.size() < BATCH_SIZE) {
                break;
            }
            afterId = executionIds.get(executionIds.size() - 1);
        }

        if (timedOut > 0) {
            log.warn("응답 없이 멈춘 채팅 실행을 타임아웃 처리함: count={}", timedOut);
        }
        return timedOut;
    }

    // 시간 초과로 끝난 실행도 기다리던 화면에 실패를 보내고, 받은 화면이 없으면 놓친 답변 알림을 남긴다.
    // 알림 저장 실패가 남은 실행의 시간 초과 처리를 막지 않게 한다
    private void notifyTimedOut(Long executionId, ChatFailure failure) {
        if (emitterRegistry.fail(executionId, failure)) {
            return;
        }
        try {
            notifications.ifAvailable(service -> service.saveUndelivered(executionId, NotificationType.ANSWER_FAILED));
        } catch (RuntimeException exception) {
            log.warn("시간 초과 알림 저장 실패: executionId={}", executionId, exception);
        }
    }
}
