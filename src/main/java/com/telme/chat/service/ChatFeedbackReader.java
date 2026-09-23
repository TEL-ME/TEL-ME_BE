package com.telme.chat.service;

import java.util.List;
import java.util.Map;

import com.telme.chat.dto.res.ChatMessageHistoryItemResponse.MyFeedback;
import com.telme.chat.entity.ChatMessage;

public interface ChatFeedbackReader {

    Map<Long, State> read(ChatActor actor, List<ChatMessage> messages);

    record State(boolean ratable, MyFeedback myFeedback) {
        public static final State UNAVAILABLE = new State(false, null);
    }
}
