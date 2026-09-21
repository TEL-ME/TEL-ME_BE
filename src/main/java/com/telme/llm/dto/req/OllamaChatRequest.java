package com.telme.llm.dto.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

import lombok.Builder;

@Builder
public record OllamaChatRequest(
        String model,
        List<Message> messages,
        boolean stream,
        String format,
        Options options
) {

    public record Message(String role, String content) {
    }

    public record Options(
            Double temperature,
            @JsonProperty("num_predict") Integer numPredict,
            @JsonProperty("num_ctx") Integer numCtx
    ) {
    }
}
