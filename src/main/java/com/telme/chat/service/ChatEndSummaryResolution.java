package com.telme.chat.service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 인접한 대화가 아니라 저장된 요청과 조건 응답의 연결로만 해결 여부를 판단한다. */
final class ChatEndSummaryResolution {
    private ChatEndSummaryResolution() {}

    static boolean isResolved(ChatEndSummaryData.Input input, long messageId) {
        var requests = input.requests().stream().filter(request -> request.originMessageId() == messageId
                || conditions(input, request).stream()
                        .anyMatch(condition -> isLinkedMessage(input, condition, messageId)))
                .toList();
        return !requests.isEmpty() && requests.stream().allMatch(request -> isResolvedRequest(input, request));
    }

    static Set<Long> findPendingOrigins(ChatEndSummaryData.Input input, Set<Long> pendingIds) {
        Set<Long> result = new LinkedHashSet<>();
        for (long id : pendingIds) {
            var own = input.requests().stream().filter(request -> request.originMessageId() == id).toList();
            var linked = input.requests().stream().filter(request -> conditions(input, request).stream()
                    .anyMatch(condition -> isLinkedMessage(input, condition, id)
                            && hasValidConditionLink(input, request, condition))).toList();
            if (own.isEmpty() && !linked.isEmpty()) {
                linked.forEach(request -> result.add(request.originMessageId()));
            } else {
                result.add(id);
            }
        }
        return Set.copyOf(result);
    }

    private static boolean isLinkedMessage(ChatEndSummaryData.Input input, ChatEndSummaryData.Condition condition,
            long messageId) {
        var asked = findMessage(input, condition.askedMessageId());
        return Long.valueOf(messageId).equals(condition.answeredMessageId())
                || asked != null && Long.valueOf(messageId).equals(asked.replyTo());
    }

    static List<ChatEndSummaryData.Message> findLatestReplies(ChatEndSummaryData.Input input, long messageId) {
        var requests = input.requests().stream().filter(request -> request.originMessageId() == messageId).toList();
        if (requests.isEmpty()) {
            var latest = findLatestReply(input, Set.of(messageId));
            return latest == null ? List.of() : List.of(latest);
        }
        Set<ChatEndSummaryData.Message> result = new LinkedHashSet<>();
        for (var request : requests) {
            Set<Long> questions = new LinkedHashSet<>();
            questions.add(messageId);
            conditions(input, request).stream().filter(condition -> condition.answeredMessageId() != null
                    && hasValidConditionLink(input, request, condition))
                    .forEach(condition -> questions.add(condition.answeredMessageId()));
            var latest = findLatestReply(input, questions);
            if (latest != null) {
                result.add(latest);
            }
        }
        return List.copyOf(result);
    }

    private static boolean isResolvedRequest(ChatEndSummaryData.Input input, ChatEndSummaryData.Request request) {
        boolean ambiguous = request.requestId() == null && input.conditions().stream()
                .anyMatch(condition -> condition.originMessageId() == request.originMessageId())
                && input.requests().stream().filter(other -> other.originMessageId() == request.originMessageId())
                        .count() > 1;
        if (ambiguous) {
            return false;
        }
        var conditions = conditions(input, request);
        if (!request.status().equals("DONE") || conditions.stream()
                .anyMatch(condition -> !Set.of("FILLED", "COORDINATES").contains(condition.status()))) {
            return false;
        }
        var replies = conditions.stream().filter(condition -> condition.answeredMessageId() != null).toList();
        if (replies.isEmpty()) {
            return hasGroundedAnswer(input, request.originMessageId());
        }
        if (replies.stream().anyMatch(condition -> !hasValidConditionLink(input, request, condition))) {
            return false;
        }
        boolean shared = replies.stream().anyMatch(condition -> input.conditions().stream()
                .filter(other -> condition.answeredMessageId().equals(other.answeredMessageId()))
                .map(ChatEndSummaryData.Condition::requestId).distinct().count() > 1);
        // 공유 응답의 통합 근거 상태만으로 각 하위 문의의 답변 성공을 증명할 수 없다.
        if (shared) {
            return false;
        }
        var last = replies.stream().max(Comparator.comparingInt(condition ->
                findMessage(input, condition.answeredMessageId()).sequence())).orElseThrow();
        Set<Long> questions = new LinkedHashSet<>();
        questions.add(request.originMessageId());
        replies.forEach(condition -> questions.add(condition.answeredMessageId()));
        var outcome = findLatestReply(input, questions);
        return hasGroundedAnswer(input, last.answeredMessageId()) && outcome != null
                && Set.of("ANSWER", "STORE_RESULT").contains(outcome.type())
                && "GROUNDED".equals(outcome.basis());
    }

    private static List<ChatEndSummaryData.Condition> conditions(ChatEndSummaryData.Input input,
            ChatEndSummaryData.Request request) {
        return input.conditions().stream().filter(condition ->
                condition.originMessageId() == request.originMessageId()
                && (request.requestId() != null ? request.requestId() == condition.requestId()
                        : input.requests().stream().filter(other ->
                                other.originMessageId() == request.originMessageId())
                                .count() == 1)).toList();
    }

    private static boolean hasValidConditionLink(ChatEndSummaryData.Input input, ChatEndSummaryData.Request request,
            ChatEndSummaryData.Condition condition) {
        var origin = findMessage(input, request.originMessageId());
        var reply = findMessage(input, condition.answeredMessageId());
        if (origin == null || reply == null || !origin.role().equals("USER") || !reply.role().equals("USER")
                || reply.sequence() <= origin.sequence() || Set.of("FAILED", "CANCELLED")
                        .contains(reply.executionStatus() == null ? "" : reply.executionStatus())
                || condition.answeredSequence() != null && condition.answeredSequence() != reply.sequence()) {
            return false;
        }
        if (condition.askedMessageId() == null) {
            // 과거 기록도 저장된 조건 응답만 사용하며 여러 하위 문의 사이의 연결은 추측하지 않는다.
            return input.requests().stream().filter(other -> other.originMessageId() == request.originMessageId())
                    .count() == 1;
        }
        var asked = findMessage(input, condition.askedMessageId());
        return asked != null && asked.role().equals("ASSISTANT") && asked.type().equals("CLARIFICATION")
                && asked.sequence() > origin.sequence() && asked.sequence() < reply.sequence()
                && isRequestQuestion(input, request, asked.replyTo(), asked.sequence());
    }

    private static boolean isRequestQuestion(ChatEndSummaryData.Input input, ChatEndSummaryData.Request request,
            Long replyTo, int askedSequence) {
        if (Long.valueOf(request.originMessageId()).equals(replyTo)) {
            return true;
        }
        var previous = findMessage(input, replyTo);
        var origin = findMessage(input, request.originMessageId());
        if (previous == null || origin == null || !previous.role().equals("USER")
                || previous.sequence() <= origin.sequence() || previous.sequence() >= askedSequence) {
            return false;
        }
        boolean linked = conditions(input, request).stream()
                .anyMatch(condition -> replyTo.equals(condition.answeredMessageId()));
        // 같은 조건을 다시 물으면 이전 응답 연결이 갱신되므로 명시된 되묻기 연결을 우선한다.
        return linked || input.requests().stream().noneMatch(other -> other.originMessageId() == replyTo);
    }

    private static boolean hasGroundedAnswer(ChatEndSummaryData.Input input, long userMessageId) {
        var user = findMessage(input, userMessageId);
        if (user == null || !user.role().equals("USER") || Set.of("FAILED", "CANCELLED")
                .contains(user.executionStatus() == null ? "" : user.executionStatus())) {
            return false;
        }
        var last = findLatestReply(input, Set.of(userMessageId));
        return last != null && last.sequence() > user.sequence()
                && Set.of("ANSWER", "STORE_RESULT").contains(last.type())
                && "GROUNDED".equals(last.basis()) && !last.content().isBlank();
    }

    private static ChatEndSummaryData.Message findLatestReply(ChatEndSummaryData.Input input, Set<Long> questions) {
        return input.messages().stream().filter(message -> message.role().equals("ASSISTANT")
                && message.replyTo() != null && questions.contains(message.replyTo()))
                .max(Comparator.comparingInt(ChatEndSummaryData.Message::sequence)).orElse(null);
    }

    private static ChatEndSummaryData.Message findMessage(ChatEndSummaryData.Input input, Long messageId) {
        return input.messages().stream().filter(message -> Objects.equals(message.id(), messageId))
                .findFirst().orElse(null);
    }
}
