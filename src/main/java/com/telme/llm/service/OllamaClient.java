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

import com.telme.llm.converter.OllamaRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.res.OllamaChatResponse;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "ollama")
@RequiredArgsConstructor
public class OllamaClient implements LlmClient {

    private static final String CHAT_PATH = "/api/chat";

    private final RestClient ollamaRestClient;
    private final OllamaRequestConverter ollamaRequestConverter;
    private final ObjectMapper objectMapper;

    @Override
    public String generate(LlmRequest request) {
        OllamaChatResponse response = ollamaRestClient.post()
                .uri(CHAT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ollamaRequestConverter.toChatRequest(request, false))
                .retrieve()
                .body(OllamaChatResponse.class);

        if (response == null || response.message() == null) {
            throw new IllegalStateException("Ollama 응답에 message가 없음");
        }
        if (!response.done()) {
            throw new IllegalStateException("Ollama 응답이 완료되지 않음");
        }
        return response.message().content();
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
                            throw new IllegalStateException("Ollama 응답 오류: " + res.getStatusCode());
                        }
                        readStreamLines(res.getBody(), handler);
                        return null;
                    });
        } catch (RuntimeException e) {
            handler.onError(e);
            return;
        }
        // try 안에 두면 onComplete 실패 시 onError까지 호출됨
        handler.onComplete();
    }

    private void readStreamLines(InputStream body, LlmStreamHandler handler) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                OllamaChatResponse chunk = objectMapper.readValue(line, OllamaChatResponse.class);
                if (chunk.message() != null && chunk.message().content() != null && !chunk.message().content().isEmpty()) {
                    handler.onToken(chunk.message().content());
                }
                if (chunk.done()) {
                    return;
                }
            }
        }
        throw new IllegalStateException("Ollama 스트림이 완료 전에 끊김");
    }
}
