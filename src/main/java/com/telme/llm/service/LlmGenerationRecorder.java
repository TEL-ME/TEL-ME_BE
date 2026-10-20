package com.telme.llm.service;

import com.telme.chat.repository.ChatExecutionRepository;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration;
import com.telme.llm.entity.LlmGeneration.Status;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.exception.LlmStreamCancelledException;
import com.telme.llm.repository.LlmGenerationRepository;
import com.telme.llm.converter.OllamaRequestConverter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.LinkedHashMap;

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
    private final OllamaRequestConverter requestConverter;
    private final ObjectMapper mapper;

    private static final String OLLAMA = "ollama";
    public static final String VLLM = "vllm";

    // 기록 실패가 LLM 응답을 막지 않도록 별도 트랜잭션으로 저장하고 예외를 삼킨다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(LlmRequest request, String model, Result result) {
        record(request, model, OLLAMA, result);
    }

    // Ollama가 아닌 호출(Bedrock 판정 등)은 provider를 같이 남긴다
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(LlmRequest request, String model, String provider, Result result) {
        if (request.executionId() == null) {
            // 채팅 실행 밖의 호출(관리자 테스트 API 등)은 의도적으로 남기지 않는다. 누락을 찾을 수 있게 흔적만 둔다
            log.debug("[LlmGenerationRecorder] 실행 ID가 없어 호출 기록 생략 taskType={}", request.taskType());
            return;
        }
        String options = requestOptions(request, model, provider);
        try {
            llmGenerationRepository.save(LlmGeneration.builder()
                    .execution(chatExecutionRepository.getReferenceById(request.executionId()))
                    .taskType(request.taskType())
                    .attempt((short) result.attempt())
                    .model(model)
                    .contextCount(request.contextCount())
                    .promptVersion(request.promptVersion())
                    .requestOptions(options)
                    .firstTokenMs(result.firstTokenMs())
                    .totalMs(result.totalMs())
                    .status(result.status())
                    .errorMessage(result.errorMessage())
                    .build());
        } catch (RuntimeException e) {
            log.warn("[LlmGenerationRecorder] 호출 기록 저장 실패 executionId={}", request.executionId(), e);
        }
    }

    // 요청 옵션은 부가 정보다. 만들지 못해도 호출 기록 행은 옵션 없이(null) 남긴다
    private String requestOptions(LlmRequest request, String model, String provider) {
        if (model == null) {
            return null;
        }
        try {
            var metadata = new LinkedHashMap<String, Object>();
            metadata.put("provider", provider);
            // vLLM은 Ollama와 같은 작업별 기본값으로 보낸다(VllmRequestConverter)
            if (OLLAMA.equals(provider) || VLLM.equals(provider)) {
                metadata.put("options", requestConverter.toChatRequest(request, false).options());
            } else {
                // null이면 클라이언트 기본값(Bedrock은 llm.bedrock.max-tokens, temperature 0)을 썼다는 뜻
                metadata.put("temperature", request.temperature());
                metadata.put("maxTokens", request.maxTokens());
            }
            metadata.put("format", request.format().name());
            metadata.put("consultRequestId", request.consultRequestId());
            return mapper.writeValueAsString(metadata);
        } catch (RuntimeException | JsonProcessingException e) {
            log.warn("[LlmGenerationRecorder] 요청 옵션 기록 생략 executionId={} type={}",
                    request.executionId(), e.getClass().getSimpleName());
            return null;
        }
    }

    public record Result(
            int attempt, Status status, Integer firstTokenMs, Integer totalMs, String errorMessage) {

        public static Result success(int attempt, Integer firstTokenMs, long totalMs) {
            return new Result(attempt, Status.SUCCESS, firstTokenMs, recordableMillis(totalMs), null);
        }

        // 근거가 없어 LLM을 호출하지 않은 경우
        public static Result noEvidence() {
            return new Result(1, Status.NO_EVIDENCE, null, null, null);
        }

        public static Result failure(int attempt, Throwable error, Integer firstTokenMs, long totalMs) {
            return new Result(attempt, toStatus(error), firstTokenMs, recordableMillis(totalMs), error.getMessage());
        }

        // INTEGER 범위를 넘는 시간은 음수로 넘치거나 최대값으로 왜곡하지 않고 측정 불가로 남긴다.
        static Integer recordableMillis(long elapsedMs) {
            return elapsedMs < 0 || elapsedMs > Integer.MAX_VALUE ? null : (int) elapsedMs;
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
