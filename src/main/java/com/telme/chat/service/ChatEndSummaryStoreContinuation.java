package com.telme.chat.service;

import com.telme.chat.converter.ChatContextFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** 요청 상태는 변경하지 않고, 바로 재시작된 동일 매장 검색의 완료만 요약에 연결한다. */
final class ChatEndSummaryStoreContinuation {
    private static final Pattern STOP = Pattern.compile("취소|그만|중단|필요없|안찾|찾지마|하지마|안할");

    private ChatEndSummaryStoreContinuation() {}

    static boolean isResolved(ChatEndSummaryData.Input input, ChatEndSummaryData.Request previous) {
        var origin = findMessage(input, previous.originMessageId());
        if (!previous.status().equals("CANCELLED") || !isSingleStoreRequest(input, previous)
                || !isValidUser(origin)) {
            return false;
        }
        var next = input.requests().stream()
                .filter(request -> sequence(input, request.originMessageId()) > origin.sequence())
                .min(Comparator.comparingInt(request -> sequence(input, request.originMessageId()))).orElse(null);
        if (next == null || !isSingleStoreRequest(input, next) || !next.status().equals("DONE")
                || !normalize(previous.query()).equals(normalize(next.query()))) {
            return false;
        }
        var resumed = findMessage(input, next.originMessageId());
        if (!isValidUser(resumed) || !hasPendingLocationLink(input, previous, origin, resumed)
                || !hasOnlyLinkedRepliesBetween(input, previous, origin, resumed)
                || !hasSameScope(input, previous, next, origin)
                || !hasMatchingConditions(input, previous, next)) {
            return false;
        }
        var replies = ChatEndSummaryResolution.findLatestReplies(input, next.originMessageId());
        return ChatEndSummaryResolution.isResolvedRequest(input, next) && !replies.isEmpty()
                && replies.stream().allMatch(reply -> reply.type().equals("STORE_RESULT") && reply.hasStoreResults());
    }

    private static boolean isSingleStoreRequest(ChatEndSummaryData.Input input, ChatEndSummaryData.Request request) {
        var classification = input.inquiryClassifications().get(request.originMessageId());
        return request.requestId() != null && classification != null && classification.intent().equals("STORE")
                && input.requests().stream().filter(value -> value.originMessageId() == request.originMessageId())
                        .count() == 1;
    }

    private static boolean hasSameScope(ChatEndSummaryData.Input input, ChatEndSummaryData.Request previous,
            ChatEndSummaryData.Request next, ChatEndSummaryData.Message origin) {
        String scope = origin.content() + " " + previous.query();
        if (!ChatEndSummaryInquiryLinker.hasSameClarificationScope(scope, next.query())) {
            return false;
        }
        Set<Long> answeredIds = conditions(input, next).stream().map(ChatEndSummaryData.Condition::answeredMessageId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        return input.messages().stream().filter(message -> message.id() == next.originMessageId()
                        || answeredIds.contains(message.id()))
                .allMatch(message -> !STOP.matcher(normalize(message.content())).find()
                        && ChatEndSummaryInquiryLinker.hasSameClarificationScope(scope, message.content()));
    }

    private static boolean hasPendingLocationLink(ChatEndSummaryData.Input input,
            ChatEndSummaryData.Request previous, ChatEndSummaryData.Message origin,
            ChatEndSummaryData.Message resumed) {
        return conditions(input, previous).stream().filter(condition -> condition.key().equals("location")
                && condition.status().equals("PENDING")).anyMatch(condition -> {
                    var asked = findMessage(input, condition.askedMessageId());
                    return asked != null && asked.role().equals("ASSISTANT") && asked.type().equals("CLARIFICATION")
                            && asked.sequence() > origin.sequence() && asked.sequence() < resumed.sequence()
                            && Long.valueOf(origin.id()).equals(asked.replyTo());
                });
    }

    private static boolean hasOnlyLinkedRepliesBetween(ChatEndSummaryData.Input input,
            ChatEndSummaryData.Request previous, ChatEndSummaryData.Message origin,
            ChatEndSummaryData.Message resumed) {
        var conditions = conditions(input, previous);
        return input.messages().stream().filter(message -> message.role().equals("USER")
                        && message.sequence() > origin.sequence() && message.sequence() < resumed.sequence())
                .allMatch(message -> isValidUser(message) && !STOP.matcher(normalize(message.content())).find()
                        && (ChatContextFormatter.isSocial(message.content())
                                || conditions.stream().anyMatch(condition -> {
                            var asked = findMessage(input, condition.askedMessageId());
                            return Long.valueOf(message.id()).equals(condition.answeredMessageId())
                                    || asked != null && asked.type().equals("CLARIFICATION")
                                            && asked.sequence() > message.sequence()
                                            && Long.valueOf(message.id()).equals(asked.replyTo());
                        })));
    }

    private static boolean hasMatchingConditions(ChatEndSummaryData.Input input, ChatEndSummaryData.Request previous,
            ChatEndSummaryData.Request next) {
        var before = conditions(input, previous);
        var after = conditions(input, next);
        if (before.stream().noneMatch(condition -> condition.key().equals("location")
                && condition.status().equals("PENDING"))) {
            return false;
        }
        // 이전 조건이 빠졌다면 새 검색 결과만으로 해결됐다고 판단하지 않는다.
        return before.stream().allMatch(condition -> !condition.status().equals("DECLINED")
                && after.stream().anyMatch(value -> value.key().equals(condition.key())
                        && Set.of("FILLED", "COORDINATES").contains(value.status())
                        && (condition.status().equals("PENDING") || condition.value() != null
                                && !condition.value().isBlank() && value.value() != null
                                && normalize(condition.value()).equals(normalize(value.value())))));
    }

    private static List<ChatEndSummaryData.Condition> conditions(ChatEndSummaryData.Input input,
            ChatEndSummaryData.Request request) {
        return input.conditions().stream().filter(condition -> request.requestId() != null
                && condition.requestId() == request.requestId()
                && condition.originMessageId() == request.originMessageId()).toList();
    }

    private static boolean isValidUser(ChatEndSummaryData.Message message) {
        return message != null && message.role().equals("USER") && !Set.of("FAILED", "CANCELLED")
                .contains(message.executionStatus() == null ? "" : message.executionStatus());
    }

    private static int sequence(ChatEndSummaryData.Input input, long messageId) {
        var message = findMessage(input, messageId);
        return message == null ? -1 : message.sequence();
    }

    private static ChatEndSummaryData.Message findMessage(ChatEndSummaryData.Input input, Long messageId) {
        return input.messages().stream().filter(message -> Objects.equals(message.id(), messageId))
                .findFirst().orElse(null);
    }

    private static String normalize(String value) {
        return ChatEndSummaryInquiryLinker.normalizeText(ConsultationQuestion.extract(value)).replaceAll("[.!?]+$", "");
    }
}
