package com.telme.chat.service;

import com.telme.chat.config.ChatExecutionProperties;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.global.common.exception.GeneralException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private static final int BATCH_SIZE = 100;

    private final ChatExecutionRepository chatExecutionRepository;
    private final ChatExecutionService chatExecutionService;
    private final ChatExecutionProperties chatExecutionProperties;

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT1M")
    public void timeOutStaleExecutions() {
        timeOutExecutionsStartedBefore(Instant.now().minus(chatExecutionProperties.runningTimeout()));
    }

    int timeOutExecutionsStartedBefore(Instant startedBefore) {
        List<Long> executionIds = chatExecutionRepository.findExecutionIdsStartedBefore(
                ChatExecution.Status.RUNNING, startedBefore, PageRequest.of(0, BATCH_SIZE));

        int timedOut = 0;
        for (Long executionId : executionIds) {
            try {
                chatExecutionService.fail(executionId, new ChatFailure(ChatMessage.Status.TIMEOUT, ERROR_CODE));
                timedOut++;
            } catch (GeneralException exception) {
                log.info("채팅 실행 타임아웃 처리 건너뜀: executionId={}, code={}",
                        executionId, exception.getErrorCode().getCode());
            }
        }

        if (timedOut > 0) {
            log.warn("응답 없이 멈춘 채팅 실행을 타임아웃 처리함: count={}", timedOut);
        }
        return timedOut;
    }
}
