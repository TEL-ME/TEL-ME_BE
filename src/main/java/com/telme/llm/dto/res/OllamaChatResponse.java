package com.telme.llm.dto.res;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaChatResponse(
        Message message,
        boolean done
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String content) {
    }
}
