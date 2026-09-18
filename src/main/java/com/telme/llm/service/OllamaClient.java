package com.telme.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.converter.OllamaRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.res.OllamaChatResponse;
import com.telme.llm.exception.LlmErrorCode;

import lombok.RequiredArgsConstructor;

@Component("baseLlmClient")
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
@RequiredArgsConstructor
public class OllamaClient implements LlmClient {

    private static final String CHAT_PATH = "/api/chat";

    private final RestClient ollamaRestClient;
    private final OllamaRequestConverter ollamaRequestConverter;
    private final ObjectMapper objectMapper;

    @Override
    public String generate(LlmRequest request) {
        try {
            OllamaChatResponse response = ollamaRestClient.post()
                    .uri(CHAT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ollamaRequestConverter.toChatRequest(request, false))
                    .retrieve()
                    .body(OllamaChatResponse.class);

            if (response == null || response.message() == null || !response.done()) {
                throw new GeneralException(LlmErrorCode.INVALID_RESPONSE);
            }
            return response.message().content();
        } catch (RuntimeException e) {
            throw toLlmException(e);
        }
    }

    @Override
    public void stream(LlmRequest request, LlmStreamHandler handler) {
        try {
            ollamaRestClient.post()
                    .uri(CHAT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ollamaRequestConverter.toChatRequest(request, true))
                    .exchange((req, res) -> {
                        if (res.getStatusCode().isError()) {
                            throw new GeneralException(LlmErrorCode.MODEL_ERROR);
                        }
                        readStreamLines(res.getBody(), handler);
                        return null;
                    });
        } catch (RuntimeException e) {
            handler.onError(toLlmException(e));
            return;
        }
        // try 안에 두면 onComplete 실패 시 onError까지 호출됨
        handler.onComplete();
    }

    // 라이브러리 예외를 실패 원인별 오류 코드로 바꾼다. 중단 예외는 그대로 넘긴다
    private RuntimeException toLlmException(RuntimeException e) {
        if (e instanceof GeneralException) {
            return e;
        }
        if (e instanceof ResourceAccessException) {
            boolean timeout = e.getCause() instanceof SocketTimeoutException;
            return new GeneralException(
                    timeout ? LlmErrorCode.TIMEOUT : LlmErrorCode.CONNECTION_FAILED);
        }
        if (e instanceof RestClientResponseException) {
            return new GeneralException(LlmErrorCode.MODEL_ERROR);
        }
        return e;
    }

    private void readStreamLines(InputStream body, LlmStreamHandler handler) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                OllamaChatResponse chunk = objectMapper.readValue(line, OllamaChatResponse.class);
                if (chunk.message() != null && chunk.message().content() != null
                        && !chunk.message().content().isEmpty()) {
                    handler.onToken(chunk.message().content());
                }
                if (chunk.done()) {
                    return;
                }
            }
        }
        throw new GeneralException(LlmErrorCode.STREAM_INTERRUPTED);
    }
}
