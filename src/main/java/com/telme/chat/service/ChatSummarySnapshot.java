package com.telme.chat.service;

import java.util.List;

record ChatSummarySnapshot(
        Long executionId,
        Long sessionId,
        String previousSummary,
        Integer expectedSequenceNo,
        Integer throughSequenceNo,
        List<ChatContextMessage> messages
) {

    ChatSummarySnapshot {
        messages = List.copyOf(messages);
    }
}
