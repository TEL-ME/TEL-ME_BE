package com.telme.llm.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// vLLM의 OpenAI 호환 채팅 응답. 일반 응답은 message, 스트리밍 조각은 delta에 글이 온다
@JsonIgnoreProperties(ignoreUnknown = true)
public record VllmChatResponse(List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message, Message delta, @JsonProperty("finish_reason") String finishReason) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String content) {
    }

    public Choice first() {
        return choices == null || choices.isEmpty() ? null : choices.get(0);
    }
}
