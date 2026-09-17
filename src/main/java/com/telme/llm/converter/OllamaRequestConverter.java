package com.telme.llm.converter;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

import com.telme.llm.config.LlmProperties;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.dto.req.OllamaChatRequest;
import com.telme.llm.dto.req.ResponseFormat;
import com.telme.llm.entity.LlmGeneration.TaskType;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OllamaRequestConverter {

    private final LlmProperties llmProperties;

    public OllamaChatRequest toChatRequest(LlmRequest request, boolean stream) {
        Defaults defaults = defaultsOf(request.taskType());

        List<OllamaChatRequest.Message> messages = new ArrayList<>();
        if (request.systemPrompt() != null) {
            messages.add(new OllamaChatRequest.Message("system", request.systemPrompt()));
        }
        messages.add(new OllamaChatRequest.Message("user", request.userPrompt()));

        return OllamaChatRequest.builder()
                .model(llmProperties.model())
                .messages(messages)
                .stream(stream)
                .format(request.format() == ResponseFormat.JSON ? "json" : null)
                .options(new OllamaChatRequest.Options(
                        request.temperature() != null ? request.temperature() : defaults.temperature(),
                        request.maxTokens() != null ? request.maxTokens() : defaults.maxTokens()))
                .build();
    }

    private Defaults defaultsOf(TaskType taskType) {
        if (taskType == null) {
            return new Defaults(0.3, 512);
        }
        return switch (taskType) {
            case ROUTING -> new Defaults(0.0, 256);
            case RAG_ANSWER -> new Defaults(0.2, 1024);
            case CLARIFICATION, FOLLOW_UP -> new Defaults(0.3, 256);
            case SUMMARY -> new Defaults(0.3, 512);
        };
    }

    private record Defaults(double temperature, int maxTokens) {
    }
}
