package com.telme.llm.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                .format(format(request))
                .think("multiturn-resolution-v8".equals(request.promptVersion())
                        && supportsThinking(llmProperties.model()) ? false : null)
                .options(new OllamaChatRequest.Options(
                        request.temperature() != null ? request.temperature() : defaults.temperature(),
                        request.maxTokens() != null ? request.maxTokens() : defaults.maxTokens(),
                        llmProperties.contextSize()))
                .build();
    }

    private boolean supportsThinking(String model) {
        return model.startsWith("qwen3:") || model.contains("EXAONE-4.0");
    }

    private Object format(LlmRequest request) {
        if (request.format() != ResponseFormat.JSON) {
            return null;
        }
        if (!"multiturn-resolution-v8".equals(request.promptVersion())) {
            return "json";
        }
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "relation", Map.of("type", "string", "enum",
                                List.of("SELF_CONTAINED", "HISTORY_DEPENDENT", "CLARIFICATION_REQUIRED")),
                        "selectedMessageIds", Map.of("type", "array", "items", Map.of("type", "integer"),
                                "uniqueItems", true)),
                "required", List.of("relation", "selectedMessageIds"),
                "additionalProperties", false);
    }

    private Defaults defaultsOf(TaskType taskType) {
        if (taskType == null) {
            return new Defaults(0.3, 512);
        }
        return switch (taskType) {
            case ROUTING, CONTEXT_RESOLUTION -> new Defaults(0.0, 512);
            // FAQ 상담은 표현을 바꿀 이유가 없다. 0.2에서 환각이 1.7배였다
            case RAG_ANSWER -> new Defaults(0.0, 1024);
            case CLARIFICATION, FOLLOW_UP -> new Defaults(0.3, 512);
            case CONDITION_EXTRACT -> new Defaults(0.0, 500);
            case SUMMARY -> new Defaults(0.3, 512);
            case SESSION_TITLE -> new Defaults(0.2, 32);
        };
    }

    private record Defaults(double temperature, int maxTokens) {
    }
}
