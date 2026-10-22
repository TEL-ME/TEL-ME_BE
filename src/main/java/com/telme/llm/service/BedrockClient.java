package com.telme.llm.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.converter.BedrockRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.exception.LlmCallException;
import com.telme.llm.exception.LlmErrorCode;
import java.util.Objects;
import java.util.Set;
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

// Bedrock Converse 호출. 검색 근거 판정처럼 짧은 응답에 쓴다. 프롬프트와 응답은 상담 내용이라 로그에 남기지 않는다
@Slf4j
@RequiredArgsConstructor
public class BedrockClient implements LlmClient {

    private static final Set<Integer> TRANSIENT_STATUSES = Set.of(408, 429, 503, 504);

    private final BedrockRuntimeClient client;
    private final BedrockRequestConverter converter;

    @Override
    public String generate(LlmRequest request) {
        ConverseResponse response;
        try {
            response = client.converse(converter.toConverseRequest(request));
        } catch (RuntimeException e) {
            throw toLlmException(e);
        }
        String text = textOf(response);
        if (text.isBlank()) {
            String detail = emptyDetail(response);
            log.warn("[Bedrock] 빈 응답 taskType={}, {}", request.taskType(), detail);
            throw new LlmCallException(LlmErrorCode.INVALID_RESPONSE, detail);
        }
        return text;
    }

    // 출력 토큰 한도에 걸려 생각만 하고 끝났는지(max_tokens), 모델이 빈 답을 줬는지(end_turn) 구분하려고 남긴다
    private String emptyDetail(ConverseResponse response) {
        if (response == null) {
            return "response=null";
        }
        Integer outputTokens = response.usage() == null ? null : response.usage().outputTokens();
        return "stopReason=" + response.stopReasonAsString() + ", outputTokens=" + outputTokens;
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
        if (e instanceof ApiCallTimeoutException || e instanceof ApiCallAttemptTimeoutException) {
            log.warn("[Bedrock] 응답 시간 초과");
            return new LlmCallException(LlmErrorCode.TIMEOUT, e.getClass().getSimpleName());
        }
        if (e instanceof AwsServiceException service) {
            String errorCode = service.awsErrorDetails() == null ? null : service.awsErrorDetails().errorCode();
            if (TRANSIENT_STATUSES.contains(service.statusCode())) {
                log.warn("[Bedrock] 일시적 오류 status={}, code={}", service.statusCode(), errorCode);
                return new LlmCallException(LlmErrorCode.TEMPORARILY_UNAVAILABLE,
                        "status=" + service.statusCode() + ", code=" + errorCode);
            }
            if (service.statusCode() >= 400 && service.statusCode() < 500) {
                log.error("[Bedrock] 설정·권한 오류 status={}, code={}", service.statusCode(), errorCode);
                return new LlmCallException(LlmErrorCode.REQUEST_REJECTED,
                        "status=" + service.statusCode() + ", code=" + errorCode);
            }
            log.warn("[Bedrock] 서버 오류 status={}, code={}", service.statusCode(), errorCode);
            return new LlmCallException(LlmErrorCode.MODEL_ERROR,
                    "status=" + service.statusCode() + ", code=" + errorCode);
        }
        if (e instanceof SdkClientException) {
            // 자격 증명이 없을 때도 여기로 온다
            log.warn("[Bedrock] 호출 실패 {}: {}", e.getClass().getSimpleName(), e.getMessage());
            // SDK 메시지에는 엔드포인트, 자격 증명 경로가 섞일 수 있어 기록에는 예외 종류만 남긴다
            return new LlmCallException(LlmErrorCode.CONNECTION_FAILED, e.getClass().getSimpleName());
        }
        log.warn("[Bedrock] 알 수 없는 호출 실패 {}", e.getClass().getSimpleName());
        return new LlmCallException(LlmErrorCode.MODEL_ERROR, e.getClass().getSimpleName());
    }
}
