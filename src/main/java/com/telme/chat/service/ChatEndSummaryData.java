package com.telme.chat.service;

import com.telme.chat.dto.res.ChatSessionEndSummaryResponse.Content;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ChatEndSummaryData {
    private ChatEndSummaryData() {}

    record Lease(long sessionId, UUID token, Instant deadline) {}

    record Message(long id, int sequence, Long replyTo, String role, String type,
            String content, String basis, String executionStatus) {}

    record Request(long originMessageId, int order, String query, String status,
            List<String> pendingQuestions, Long requestId) {
        Request(long originMessageId, int order, String query, String status) {
            this(originMessageId, order, query, status, List.of(), null);
        }

        Request(long originMessageId, int order, String query, String status, List<String> pendingQuestions) {
            this(originMessageId, order, query, status, pendingQuestions, null);
        }

        Request {
            pendingQuestions = List.copyOf(pendingQuestions);
        }
    }

    record Condition(long requestId, long originMessageId, String key, String value, String status,
            String source, Long answeredMessageId, Integer answeredSequence) {}

    record Input(long sessionId, int throughSequence, Long executionId,
            List<Message> messages, List<Request> requests, Set<Long> pendingIds, List<Condition> conditions) {
        Input(long sessionId, int throughSequence, Long executionId,
                List<Message> messages, List<Request> requests, Set<Long> pendingIds) {
            this(sessionId, throughSequence, executionId, messages, requests, pendingIds, List.of());
        }

        Input {
            messages = List.copyOf(messages);
            requests = List.copyOf(requests);
            pendingIds = Set.copyOf(pendingIds);
            conditions = List.copyOf(conditions);
        }
    }

    record Result(String json, Content content) {}
}
