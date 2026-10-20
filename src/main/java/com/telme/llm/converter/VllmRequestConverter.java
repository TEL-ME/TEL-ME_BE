package com.telme.llm.converter;

import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.OllamaChatRequest;
import com.telme.llm.dto.req.VllmChatRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// LlmRequest → vLLM 요청. 작업별 temperature·출력 한도는 Ollama와 같은 값을 써야 서빙만 바꿔 비교할 수 있어
// Ollama 변환 결과를 그대로 옮긴다. 컨텍스트 크기는 vLLM 서버의 --max-model-len이 정한다
@Component
@ConditionalOnProperty(name = "llm.provider", havingValue = "vllm")
@RequiredArgsConstructor
public class VllmRequestConverter {

    private final OllamaRequestConverter ollamaRequestConverter;

    public VllmChatRequest toChatRequest(LlmRequest request, boolean stream) {
        OllamaChatRequest base = ollamaRequestConverter.toChatRequest(request, stream);
        return VllmChatRequest.builder()
                .model(base.model())
                .messages(base.messages().stream()
                        .map(message -> new VllmChatRequest.Message(message.role(), message.content()))
                        .toList())
                .stream(stream)
                .temperature(base.options().temperature())
                .maxTokens(base.options().numPredict())
                .responseFormat(responseFormat(base.format()))
                .build();
    }

    // Ollama의 "json"은 JSON 모드, 스키마는 vLLM의 스키마 강제(guided decoding)로 보낸다
    private Map<String, Object> responseFormat(Object format) {
        if (format == null) {
            return null;
        }
        if (format instanceof Map<?, ?> schema) {
            return Map.of("type", "json_schema", "json_schema", Map.of("name", "response", "schema", schema));
        }
        return Map.of("type", "json_object");
    }
}
