package com.telme.chat.converter;

import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.entity.ChatExecution;
import com.telme.chat.entity.ChatMessage;
import org.springframework.stereotype.Component;

@Component
public class ChatMessageConverter {

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
}
