package com.telme.chat.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.dto.res.ChatStoreSearchContextResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ChatAnswer(
        ChatMessage.MessageType messageType,
        String content,
        ChatMessage.AnswerBasis answerBasis,
        List<String> followUps,
        List<Map<String, Object>> storeResults,
        ChatStoreSearchContextResponse storeSearchContext,
        // FAQ에서 근거를 찾지 못한 답변이라 프론트가 상담원 연결을 안내한다. 저장하지 않고 완료 이벤트로만 내려간다
        boolean handoff
) {

    public ChatAnswer(ChatMessage.MessageType messageType, String content,
            ChatMessage.AnswerBasis answerBasis, List<String> followUps, List<Map<String, Object>> storeResults) {
        this(messageType, content, answerBasis, followUps, storeResults, null, false);
    }

    public ChatAnswer(ChatMessage.MessageType messageType, String content, ChatMessage.AnswerBasis answerBasis,
            List<String> followUps, List<Map<String, Object>> storeResults,
            ChatStoreSearchContextResponse storeSearchContext) {
        this(messageType, content, answerBasis, followUps, storeResults, storeSearchContext, false);
    }

    public ChatAnswer withHandoff(boolean handoff) {
        return new ChatAnswer(messageType, content, answerBasis, followUps, storeResults, storeSearchContext, handoff);
    }

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
