package com.telme.chat.service;

import com.telme.chat.entity.ChatMessage;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ChatAnswer(
        ChatMessage.MessageType messageType,
        String content,
        ChatMessage.AnswerBasis answerBasis,
        List<String> followUps,
        List<Map<String, Object>> storeResults
) {

    public ChatAnswer {
        Objects.requireNonNull(messageType, "messageType");
        if (messageType != ChatMessage.MessageType.ANSWER
                && messageType != ChatMessage.MessageType.STORE_RESULT) {
            throw new IllegalArgumentException("답변 메시지 유형은 ANSWER 또는 STORE_RESULT만 가능합니다: " + messageType);
        }
        if (messageType == ChatMessage.MessageType.ANSWER
                && (content == null || content.isBlank())) {
            throw new IllegalArgumentException("일반 답변 내용은 비어 있을 수 없습니다.");
        }
        if (messageType == ChatMessage.MessageType.STORE_RESULT
                && (storeResults == null || storeResults.isEmpty())) {
            throw new IllegalArgumentException("매장 결과 답변에는 매장 정보가 필요합니다.");
        }
    }
}
