package com.telme.llm.service;

import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration;
import com.telme.llm.entity.LlmGeneration.Status;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.repository.LlmGenerationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class LlmGenerationRecorder {

    private final LlmGenerationRepository llmGenerationRepository;
    private final ChatExecutionRepository chatExecutionRepository;

    // 기록 실패가 LLM 응답을 막지 않도록 별도 트랜잭션으로 저장하고 예외를 삼킨다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(LlmRequest request, String model, Result result) {
        if (request.executionId() == null) {
            return;
        }
        try {
            llmGenerationRepository.save(LlmGeneration.builder()
                    .execution(chatExecutionRepository.getReferenceById(request.executionId()))
                    .taskType(request.taskType())
                    .attempt((short) result.attempt())
                    .model(model)
                    .contextCount(request.contextCount())
                    .promptVersion(request.promptVersion())
                    .firstTokenMs(result.firstTokenMs())
                    .totalMs(result.totalMs())
                    .status(result.status())
                    .errorMessage(result.errorMessage())
                    .build());
        } catch (RuntimeException e) {
            log.warn("[LlmGenerationRecorder] 호출 기록 저장 실패 executionId={}", request.executionId(), e);
        }
    }

    public record Result(
            int attempt, Status status, Integer firstTokenMs, Integer totalMs, String errorMessage) {

        public static Result success(int attempt, Integer firstTokenMs, long totalMs) {
            return new Result(attempt, Status.SUCCESS, firstTokenMs, (int) totalMs, null);
        }

        // 근거가 없어 LLM을 호출하지 않은 경우
        public static Result noEvidence() {
            return new Result(1, Status.NO_EVIDENCE, null, null, null);
        }

        public static Result failure(int attempt, Throwable error, Integer firstTokenMs, long totalMs) {
            return new Result(attempt, toStatus(error), firstTokenMs, (int) totalMs, error.getMessage());
        }

        private static Status toStatus(Throwable error) {
            if (error instanceof LlmStreamCancelledException) {
                return Status.CANCELLED;
            }
            if (error instanceof GeneralException general
                    && general.getErrorCode() instanceof LlmErrorCode code) {
                return switch (code) {
                    case TIMEOUT -> Status.TIMEOUT;
                    case CONNECTION_FAILED -> Status.CONNECTION_FAILED;
                    default -> Status.MODEL_ERROR;
                };
            }
            return Status.MODEL_ERROR;
        }
    }
}
