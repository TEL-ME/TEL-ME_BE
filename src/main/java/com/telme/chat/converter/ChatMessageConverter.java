package com.telme.chat.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.dto.res.ChatMessageHistoryItemResponse;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageConverter {

    private static final TypeReference<List<String>> FOLLOW_UPS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> STORE_RESULTS_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public ChatMessageSendResponse toSendResponse(ChatMessage message, ChatExecution execution) {
        return new ChatMessageSendResponse(
                message.getSession().getSessionId(),
                message.getMessageId(),
                message.getSequenceNo(),
                execution.getExecutionId(),
                execution.getStatus().name(),
                message.getCreatedAt()
        );
    }

    public ChatMessageHistoryItemResponse toHistoryItemResponse(ChatMessage message) {
        return new ChatMessageHistoryItemResponse(
                message.getMessageId(),
                message.getSequenceNo(),
                message.getReplyTo() == null ? null : message.getReplyTo().getMessageId(),
                message.getRole().name(),
                message.getMessageType().name(),
                message.getContent(),
                message.getStatus() == null ? null : message.getStatus().name(),
                message.getAnswerBasis() == null ? null : message.getAnswerBasis().name(),
                parseFollowUps(message),
                parseStoreResults(message),
                message.getCreatedAt(),
                message.getCompletedAt()
        );
    }

    private List<String> parseFollowUps(ChatMessage message) {
        JsonNode root = readJson(message, "followUps", message.getFollowUps(), "ARRAY_OF_STRING");
        if (root == null || root.isNull()) {
            return null;
        }
        if (!root.isArray()) {
            return invalidShape(message, "followUps", "ARRAY_OF_STRING", root.getNodeType().name());
        }

        for (int index = 0; index < root.size(); index++) {
            JsonNode item = root.get(index);
            if (!item.isTextual()) {
                return invalidShape(
                        message,
                        "followUps",
                        "ARRAY_OF_STRING",
                        arrayItemType(item, index)
                );
            }
        }
        return convertArray(message, "followUps", root, FOLLOW_UPS_TYPE, "ARRAY_OF_STRING");
    }

    private List<Map<String, Object>> parseStoreResults(ChatMessage message) {
        JsonNode root = readJson(message, "storeResults", message.getStoreResults(), "ARRAY_OF_OBJECT");
        if (root == null || root.isNull()) {
            return null;
        }
        if (!root.isArray()) {
            return invalidShape(message, "storeResults", "ARRAY_OF_OBJECT", root.getNodeType().name());
        }

        for (int index = 0; index < root.size(); index++) {
            JsonNode item = root.get(index);
            if (!item.isObject()) {
                return invalidShape(
                        message,
                        "storeResults",
                        "ARRAY_OF_OBJECT",
                        arrayItemType(item, index)
                );
            }
        }

        return convertArray(message, "storeResults", root, STORE_RESULTS_TYPE, "ARRAY_OF_OBJECT");
    }

    private <T> List<T> convertArray(
            ChatMessage message,
            String fieldName,
            JsonNode root,
            TypeReference<List<T>> type,
            String expected
    ) {
        try {
            List<T> result = objectMapper.convertValue(root, type);
            return Collections.unmodifiableList(result);
        } catch (IllegalArgumentException exception) {
            return invalidShape(message, fieldName, expected, "CONVERSION_FAILED");
        }
    }

    private JsonNode readJson(ChatMessage message, String fieldName, String value, String expected) {
        if (value == null) {
            return null;
        }

        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            return invalidShape(message, fieldName, expected, "INVALID_JSON");
        }
    }

    private String arrayItemType(JsonNode item, int index) {
        return "ARRAY_ITEM_" + item.getNodeType().name() + "[index=" + index + "]";
    }

    private <T> T invalidShape(ChatMessage message, String fieldName, String expected, String actual) {
        log.warn(
                "채팅 메시지 JSON 형태 불일치: sessionId={}, messageId={}, field={}, expected={}, actual={}",
                message.getSession().getSessionId(),
                message.getMessageId(),
                fieldName,
                expected,
                actual
        );
        return null;
    }
}
