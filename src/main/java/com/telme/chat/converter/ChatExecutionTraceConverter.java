package com.telme.chat.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.telme.chat.dto.res.ChatExecutionTraceResponse;
import com.telme.global.common.code.CommonErrorCode;
import com.telme.global.common.exception.GeneralException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatExecutionTraceConverter {
    private final ObjectMapper mapper;

    public ChatExecutionTraceResponse toResponse(Map<String, Object> execution,
            List<Map<String, Object>> routing, List<Map<String, Object>> attempts) {
        String trace = (String) execution.get("trace");
        JsonNode steps = readStoredJson(trace);
        if (steps.has("generationInputs") && steps.path("generationInputs").size() == 1) {
            // 단일 생성의 기존 별칭을 유지하고 여러 생성은 배열로만 구분한다.
            ((ObjectNode) steps).set("generationInput", steps.path("generationInputs").get(0));
        }
        var modelAttempts = attempts.stream().map(this::toModelAttempt).toList();
        return ChatExecutionTraceResponse.builder()
                .executionId((Long) execution.get("executionId"))
                .sessionId((Long) execution.get("sessionId"))
                .inputMessageId((Long) execution.get("inputMessageId"))
                .outputMessageId((Long) execution.get("outputMessageId"))
                .status((String) execution.get("status"))
                .errorCode((String) execution.get("errorCode"))
                .originalUserMessage((String) execution.get("originalUserMessage"))
                .finalAnswer((String) execution.get("finalAnswer"))
                .answerStatus((String) execution.get("answerStatus"))
                .answerBasis((String) execution.get("answerBasis"))
                .traceRecorded(trace != null)
                .steps(steps)
                .routing(routing)
                .modelAttempts(modelAttempts)
                .build();
    }

    private Map<String, Object> toModelAttempt(Map<String, Object> attempt) {
        Map<String, Object> result = new LinkedHashMap<>(attempt);
        result.put("configuration", readStoredJson((String) attempt.get("configuration")));
        return result;
    }

    private JsonNode readStoredJson(String json) {
        if (json == null) return mapper.nullNode();
        try {
            return mapper.readTree(json);
        } catch (JsonProcessingException failure) {
            // 저장 내용이나 예외 본문을 외부에 노출하지 않고 기존 500 오류 계약을 유지한다.
            throw new GeneralException(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
