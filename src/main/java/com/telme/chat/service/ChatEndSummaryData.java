package com.telme.chat.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.telme.chat.dto.res.ChatSessionEndSummaryResponse.Content;
import com.telme.chat.repository.ChatEndSummaryInquiryRepository.Classification;
import com.telme.chat.safety.OutputMaskContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ChatEndSummaryData {
    private ChatEndSummaryData() {}

    record Lease(long sessionId, UUID token, Instant deadline) {}

    record Message(long id, int sequence, Long replyTo, String role, String type,
            String content, String basis, String executionStatus, @JsonIgnore boolean hasStoreResults) {
        Message(long id, int sequence, Long replyTo, String role, String type,
                String content, String basis, String executionStatus) {
            this(id, sequence, replyTo, role, type, content, basis, executionStatus, false);
        }
    }

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
            String source, Long answeredMessageId, Integer answeredSequence, Long askedMessageId) {
        Condition(long requestId, long originMessageId, String key, String value, String status,
                String source, Long answeredMessageId, Integer answeredSequence) {
            this(requestId, originMessageId, key, value, status, source, answeredMessageId, answeredSequence, null);
        }
    }

    record Input(long sessionId, int throughSequence, Long executionId,
            List<Message> messages, List<Request> requests, Set<Long> pendingIds, List<Condition> conditions,
            OutputMaskContext maskContext, Map<Long, Classification> inquiryClassifications) {
        Input(long sessionId, int throughSequence, Long executionId,
                List<Message> messages, List<Request> requests, Set<Long> pendingIds, List<Condition> conditions,
                OutputMaskContext maskContext) {
            this(sessionId, throughSequence, executionId, messages, requests, pendingIds, conditions,
                    maskContext, Map.of());
        }

        Input {
            messages = List.copyOf(messages);
            requests = List.copyOf(requests);
            pendingIds = Set.copyOf(pendingIds);
            conditions = List.copyOf(conditions);
            inquiryClassifications = Map.copyOf(inquiryClassifications);
        }
    }

    record Result(String json, Content content, OutputMaskContext maskContext) {}
}
