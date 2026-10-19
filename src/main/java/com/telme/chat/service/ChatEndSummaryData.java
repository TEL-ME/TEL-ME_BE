package com.telme.chat.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.telme.chat.dto.res.ChatSessionEndSummaryResponse.Content;
import com.telme.chat.safety.OutputMaskContext;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ChatEndSummaryData {
    private ChatEndSummaryData() {}

    record Lease(long sessionId, UUID token, Instant deadline) {}

    record Message(long id, int sequence, Long replyTo, String role, String type,
            String content, String basis, String executionStatus) {}

    /**
     * outcome·answerText·answerMessageId·displayQuestion은 실시간 경로가 하위 질문마다 기록한 결과다(V41, AnswerOutcome).
     * 그 전에 저장된 상담은 null이다. answerText는 안내 후보로만 쓰고 모델 입력에는 넣지 않는다.
     */
    record Request(long originMessageId, int order, String query, String status,
            List<String> pendingQuestions, Long requestId, String outcome,
            @JsonIgnore String answerText, @JsonIgnore Long answerMessageId, String displayQuestion, String intent) {
        Request(long originMessageId, int order, String query, String status) {
            this(originMessageId, order, query, status, List.of(), null);
        }

        Request(long originMessageId, int order, String query, String status, List<String> pendingQuestions) {
            this(originMessageId, order, query, status, pendingQuestions, null);
        }

        Request(long originMessageId, int order, String query, String status, List<String> pendingQuestions,
                Long requestId) {
            this(originMessageId, order, query, status, pendingQuestions, requestId, null, null, null, null, null);
        }

        Request {
            pendingQuestions = List.copyOf(pendingQuestions);
        }

        Request withPendingQuestions(List<String> questions) {
            return new Request(originMessageId, order, query, status, questions, requestId, outcome, answerText,
                    answerMessageId, displayQuestion, intent);
        }
    }

    record Condition(long requestId, long originMessageId, String key, String value, String status,
            String source, Long answeredMessageId, Integer answeredSequence) {}

    record Input(long sessionId, int throughSequence, Long executionId,
            List<Message> messages, List<Request> requests, Set<Long> pendingIds, List<Condition> conditions,
            OutputMaskContext maskContext) {
        Input {
            messages = List.copyOf(messages);
            requests = List.copyOf(requests);
            pendingIds = Set.copyOf(pendingIds);
            conditions = List.copyOf(conditions);
        }

        /** 실시간 경로가 하위 질문 결과를 기록한 뒤의 상담이면 그 결과로 안내와 남은 사항을 정한다 */
        boolean recordsOutcomes() {
            return requests.stream().anyMatch(request -> request.outcome() != null);
        }
    }

    record Result(String json, Content content, OutputMaskContext maskContext) {}
}
