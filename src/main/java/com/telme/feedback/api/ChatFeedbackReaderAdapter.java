package com.telme.feedback.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.telme.chat.dto.res.ChatMessageHistoryItemResponse.MyFeedback;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatFeedbackReader;
import com.telme.feedback.dto.FeedbackModels;
import com.telme.feedback.dto.FeedbackModels.Feedback;
import com.telme.feedback.service.FeedbackService;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ChatFeedbackReaderAdapter implements ChatFeedbackReader {
    private final FeedbackService service;

    @Override
    public Map<Long, State> read(ChatActor actor, List<ChatMessage> messages) {
        // 평가할 수 없는 메세지에는 피드백이 있을 수 없으므로 조회 대상에서 제외한다.
        List<ChatMessage> ratable = messages.stream().filter(this::isRatable).toList();
        Map<Long, Feedback> mine = service.getAll(
                ratable.stream().map(ChatMessage::getMessageId).toList(),
                ChatFeedbackActorResolver.toActor(actor));
        
        Map<Long, State> states = new HashMap<>();
        for (ChatMessage message : ratable) {
            states.put(message.getMessageId(), new State(true, toMyFeedback(mine.get(message.getMessageId()))));
        }
        return states;
    }
    
    private boolean isRatable(ChatMessage message) {
        return FeedbackModels.isRatable(
                message.getRole().name(), 
                message.getMessageType().name(), 
                message.getStatus() == null ? null : message.getStatus().name());
    }
    
    private MyFeedback toMyFeedback(Feedback feedback) {
        if (feedback == null) {
            return null;
        }
        var input = feedback.input();
        return new MyFeedback(
                input.rating().name(),
                input.reason() == null ? null : input.reason().name(),
                input.comment());
    }
}
