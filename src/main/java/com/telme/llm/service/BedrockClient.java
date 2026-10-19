package com.telme.llm.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.converter.BedrockRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.exception.BedrockTemporaryException;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;

// Bedrock Converse 호출. 검색 근거 판정처럼 짧은 응답에 쓴다. 프롬프트와 응답은 상담 내용이라 로그에 남기지 않는다
@Slf4j
@RequiredArgsConstructor
public class BedrockClient implements LlmClient {

    private final BedrockRuntimeClient client;
    private final BedrockRequestConverter converter;
    private final boolean requireCompleteResponse;

    public BedrockClient(BedrockRuntimeClient client, BedrockRequestConverter converter) {
        this(client, converter, false);
    }

    @Override
    public String generate(LlmRequest request) {
        ConverseResponse response;
        try {
            response = client.converse(converter.toConverseRequest(request));
        } catch (RuntimeException e) {
            throw toLlmException(e);
        }
        // 종료 요약은 정상 종료된 응답만 받아 출력 잘림과 보안 차단을 성공으로 저장하지 않는다.
        if (requireCompleteResponse && (response == null || response.stopReason() != StopReason.END_TURN)) {
            throw new GeneralException(LlmErrorCode.INVALID_RESPONSE);
        }
        String text = textOf(response);
        if (text.isBlank()) {
            throw new GeneralException(LlmErrorCode.INVALID_RESPONSE);
        }
        return text;
    }

    // 짧은 응답만 쓰므로 스트리밍 API를 따로 쓰지 않고, 다 받은 뒤 한 번에 넘긴다
    @Override
    public void stream(LlmRequest request, LlmStreamHandler handler) {
        String text;
        try {
            text = generate(request);
        } catch (RuntimeException e) {
            handler.onError(e);
            return;
        }
        handler.onToken(text);
        handler.onComplete();
    }

    // gpt-oss는 생각 과정을 reasoningContent 블록으로 따로 준다. 답은 text 블록만 모은다
    private String textOf(ConverseResponse response) {
        if (response == null || response.output() == null || response.output().message() == null) {
            return "";
        }
        return response.output().message().content().stream()
                .map(ContentBlock::text)
                .filter(Objects::nonNull)
                .collect(Collectors.joining())
                .strip();
    }

    private GeneralException toLlmException(RuntimeException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof BedrockTemporaryException temporary) {
                return temporary;
            }
        }
        if (e instanceof ApiCallTimeoutException || e instanceof ApiCallAttemptTimeoutException) {
            log.warn("[Bedrock] 응답 시간 초과");
            return new GeneralException(LlmErrorCode.TIMEOUT);
        }
        if (e instanceof AwsServiceException service) {
            String errorCode = service.awsErrorDetails() == null ? null : service.awsErrorDetails().errorCode();
            if (service.statusCode() == 408) {
                log.warn("[Bedrock] 모델 처리 시간 초과 status={}, code={}", service.statusCode(), errorCode);
                return new GeneralException(LlmErrorCode.TIMEOUT);
            }
            if (service.statusCode() == 429 || service.statusCode() == 500 || service.statusCode() == 502
                    || service.statusCode() == 503 || service.statusCode() == 504) {
                log.warn("[Bedrock] 일시 서버 오류·요청 한도 초과 status={}, code={}", service.statusCode(), errorCode);
                return new BedrockTemporaryException(LlmErrorCode.MODEL_ERROR);
            }
            if (service.statusCode() >= 400 && service.statusCode() < 500 && service.statusCode() != 429) {
                log.error("[Bedrock] 설정·권한 오류 status={}, code={}", service.statusCode(), errorCode);
            } else {
                log.warn("[Bedrock] 서버 오류·한도 초과 status={}, code={}", service.statusCode(), errorCode);
            }
            return new GeneralException(LlmErrorCode.MODEL_ERROR);
        }
        if (e instanceof SdkClientException) {
            // 자격 증명이 없을 때도 여기로 온다
            log.warn("[Bedrock] 호출 실패 {}: {}", e.getClass().getSimpleName(), e.getMessage());
            return new GeneralException(LlmErrorCode.CONNECTION_FAILED);
        }
        log.warn("[Bedrock] 알 수 없는 호출 실패 {}", e.getClass().getSimpleName());
        return new GeneralException(LlmErrorCode.MODEL_ERROR);
    }
}
