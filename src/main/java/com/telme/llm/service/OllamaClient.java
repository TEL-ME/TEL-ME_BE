package com.telme.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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

    private RuntimeException toLlmException(RuntimeException e) {
        return LlmHttpErrors.toLlmException(e);
    }

    private void readStreamLines(InputStream body, LlmStreamHandler handler) throws IOException {
        boolean tokenEmitted = false;
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
                    tokenEmitted = true;
                }
                if (chunk.done()) {
                    // 여기서 실패로 처리해야 호출 기록도 실패로 남는다
                    if (!tokenEmitted) {
                        throw new GeneralException(LlmErrorCode.INVALID_RESPONSE);
                    }
                    return;
                }
            }
        }
        throw new GeneralException(LlmErrorCode.STREAM_INTERRUPTED);
    }
}
