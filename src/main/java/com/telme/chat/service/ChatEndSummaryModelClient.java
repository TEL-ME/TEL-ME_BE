package com.telme.chat.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.exception.BedrockTemporaryException;
import com.telme.llm.service.LlmAttemptClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class ChatEndSummaryModelClient implements ChatEndSummaryModel {
    private final LlmAttemptClient bedrock;
    private final LlmAttemptClient fallback;
    private final Clock clock;
    private final Duration bedrockBudget;
    private final Duration fallbackBudget;

    @Override
    public String generate(LlmRequest request, Instant deadline) {
        if (request.taskType() != TaskType.SUMMARY) {
            throw new IllegalArgumentException("종료 요약 전용 모델은 요약 작업에만 사용할 수 있습니다.");
        }
        checkBudget(deadline, bedrockBudget);
        try {
            return bedrock.generate(prepareBedrockRequest(request), 1);
        } catch (GeneralException exception) {
            // 인증·권한·차단 오류는 우회하지 않고 확인된 일시 장애만 한 번 대체한다.
            if (exception.getErrorCode() != LlmErrorCode.TIMEOUT
                    && !(exception instanceof BedrockTemporaryException)) {
                throw exception;
            }
            checkBudget(deadline, fallbackBudget);
            log.info("상담 종료 요약의 베드락 일시 장애로 엑사원으로 한 번 대체합니다. code={}",
                    exception.getErrorCode().getCode());
            return fallback.generate(request, 2);
        }
    }

    private LlmRequest prepareBedrockRequest(LlmRequest request) {
        // 베드락은 공통 프롬프트로 JSON을 요구하고, 엑사원 대체 호출에는 원래 스키마를 유지한다.
        return new LlmRequest(request.executionId(), request.consultRequestId(), request.taskType(),
                request.systemPrompt(), request.userPrompt(), ResponseFormat.TEXT, request.temperature(),
                request.maxTokens(), request.contextCount(), request.promptVersion(), null, request.think());
    }

    private void checkBudget(Instant deadline, Duration required) {
        if (Thread.currentThread().isInterrupted()) {
            throw new GeneralException(LlmErrorCode.STREAM_INTERRUPTED);
        }
        Instant now = clock.instant();
        if (deadline == null || !deadline.isAfter(now) || Duration.between(now, deadline).compareTo(required) < 0) {
            throw new IllegalArgumentException("WORK_LIMIT");
        }
    }
}
