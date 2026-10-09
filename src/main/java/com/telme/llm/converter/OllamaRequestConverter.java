package com.telme.llm.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
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
    private static final com.fasterxml.jackson.databind.ObjectMapper SCHEMA_INPUT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

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
                .think(isContextResolution(request)
                        && supportsThinking(llmProperties.model()) ? false : null)
                .options(new OllamaChatRequest.Options(
                        request.temperature() != null ? request.temperature() : defaults.temperature(),
                        request.maxTokens() != null ? request.maxTokens() : defaults.maxTokens(),
                        llmProperties.contextSize()))
                .build();
    }

    private boolean isContextResolution(LlmRequest request) {
        return "multiturn-resolution-v8".equals(request.promptVersion())
                || "multiturn-resolution-v18".equals(request.promptVersion())
                || "multiturn-resolution-v30-reference".equals(request.promptVersion())
                || "multiturn-resolution-v30-sources".equals(request.promptVersion());
    }

    private boolean supportsThinking(String model) {
        return model.startsWith("qwen3:") || model.contains("EXAONE-4.0");
    }

    private Object format(LlmRequest request) {
        if (request.format() != ResponseFormat.JSON) {
            return null;
        }
        if (!isContextResolution(request)) {
            return "json";
        }
        if ("multiturn-resolution-v30-reference".equals(request.promptVersion())) {
            return Map.of("type", "object", "properties", Map.of("reference",
                    Map.of("type", "string", "enum", List.of("EXPLICIT", "OMITTED", "AMBIGUOUS"))),
                    "required", List.of("reference"), "additionalProperties", false);
        }
        if ("multiturn-resolution-v30-sources".equals(request.promptVersion())) {
            int sourceCount = contextSourceCount(request.userPrompt());
            Map<String, Object> count = Map.of("type", "integer", "minimum", 0, "maximum", 8);
            Map<String, Object> source = new LinkedHashMap<>();
            source.put("index", Map.of("type", "integer", "minimum", 1, "maximum", sourceCount));
            source.put("targetCount", count);
            source.put("parentIndex", Map.of("type", "integer", "minimum", 0, "maximum", Math.max(0, sourceCount - 1)));
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("sources", Map.of("type", "array", "minItems", sourceCount, "maxItems", sourceCount,
                    "items", Map.of("type", "object",
                    "properties", source, "required", List.of("index", "targetCount", "parentIndex"),
                    "additionalProperties", false)));
            properties.put("referenceScope", Map.of("type", "string", "enum", List.of("RECENT", "POSITION")));
            properties.put("anchorIndex", Map.of("type", "integer", "minimum", 0, "maximum", sourceCount));
            return Map.of("type", "object", "properties", properties,
                    "required", List.of("sources", "referenceScope", "anchorIndex"), "additionalProperties", false);
        }
        // 관계를 먼저 판정한 뒤 출처를 선택하도록 속성 순서를 고정한다.
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("relation", Map.of("type", "string", "enum",
                List.of("SELF_CONTAINED", "HISTORY_DEPENDENT", "CLARIFICATION_REQUIRED")));
        properties.put("selectedMessageIds", Map.of("type", "array", "items", Map.of("type", "integer"),
                "uniqueItems", true));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("relation", "selectedMessageIds"),
                "additionalProperties", false);
    }

    // 문맥 판정기가 만든 입력만 읽고 전역 요청 파싱 설정은 변경하지 않는다.
    private int contextSourceCount(String input) {
        try {
            var root = SCHEMA_INPUT_MAPPER.readTree(input);
            if (root == null || !root.path("sourceMessages").isArray()) {
                throw new IllegalArgumentException("문맥 판정 입력의 이전 발언 목록이 필요합니다.");
            }
            return root.path("sourceMessages").size();
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalArgumentException("문맥 판정 입력 형식이 올바르지 않습니다.", invalid);
        }
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
