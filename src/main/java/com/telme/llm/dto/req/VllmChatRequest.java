package com.telme.llm.dto.req;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.Builder;

// vLLM의 OpenAI 호환 채팅 요청(/v1/chat/completions)
@Builder
public record VllmChatRequest(
        String model,
        List<Message> messages,
        boolean stream,
        Double temperature,
        @JsonProperty("max_tokens") Integer maxTokens,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("response_format") Map<String, Object> responseFormat
) {

    public record Message(String role, String content) {
    }
}
