package com.telme.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.converter.VllmRequestConverter;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.res.VllmChatResponse;
import com.telme.llm.exception.LlmErrorCode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// vLLM의 OpenAI 호환 API로 EXAONE을 부른다. 재시도와 호출 기록은 LlmConfig가 Ollama와 같은 방식으로 감싼다
@Component("baseLlmClient")
@ConditionalOnProperty(name = "llm.provider", havingValue = "vllm")
@RequiredArgsConstructor
public class VllmClient implements LlmClient {

    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String DATA_PREFIX = "data:";
    private static final String DONE = "[DONE]";

    private final RestClient vllmRestClient;
    private final VllmRequestConverter vllmRequestConverter;
    private final ObjectMapper objectMapper;

    @Override
    public String generate(LlmRequest request) {
        try {
            VllmChatResponse response = vllmRestClient.post()
                    .uri(CHAT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(vllmRequestConverter.toChatRequest(request, false))
                    .retrieve()
                    .body(VllmChatResponse.class);

            VllmChatResponse.Choice choice = response == null ? null : response.first();
            if (choice == null || choice.message() == null || choice.message().content() == null) {
                throw new GeneralException(LlmErrorCode.INVALID_RESPONSE);
            }
            return choice.message().content();
        } catch (RuntimeException e) {
            throw LlmHttpErrors.toLlmException(e);
        }
    }

    @Override
    public void stream(LlmRequest request, LlmStreamHandler handler) {
        try {
            vllmRestClient.post()
                    .uri(CHAT_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(vllmRequestConverter.toChatRequest(request, true))
                    .exchange((req, res) -> {
                        if (res.getStatusCode().isError()) {
                            throw new GeneralException(LlmErrorCode.MODEL_ERROR);
                        }
                        readEvents(res.getBody(), handler);
                        return null;
                    });
        } catch (RuntimeException e) {
            handler.onError(LlmHttpErrors.toLlmException(e));
            return;
        }
        // try 안에 두면 onComplete 실패 시 onError까지 호출됨
        handler.onComplete();
    }

    // 서버 전송 이벤트(SSE)의 data 줄을 읽는다. [DONE]이 오기 전에 끊기면 중단으로 본다
    private void readEvents(InputStream body, LlmStreamHandler handler) throws IOException {
        boolean tokenEmitted = false;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(DATA_PREFIX)) {
                    continue;
                }
                String data = line.substring(DATA_PREFIX.length()).strip();
                if (DONE.equals(data)) {
                    // 여기서 실패로 처리해야 호출 기록도 실패로 남는다
                    if (!tokenEmitted) {
                        throw new GeneralException(LlmErrorCode.INVALID_RESPONSE);
                    }
                    return;
                }
                VllmChatResponse.Choice choice = objectMapper.readValue(data, VllmChatResponse.class).first();
                if (choice != null && choice.delta() != null && choice.delta().content() != null
                        && !choice.delta().content().isEmpty()) {
                    handler.onToken(choice.delta().content());
                    tokenEmitted = true;
                }
            }
        }
        throw new GeneralException(LlmErrorCode.STREAM_INTERRUPTED);
    }
}
