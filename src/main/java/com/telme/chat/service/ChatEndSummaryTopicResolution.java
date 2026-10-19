package com.telme.chat.service;

import com.telme.chat.converter.ChatContextFormatter;
import java.util.Comparator;
import java.util.Set;

/** 상담 요청이 없는 주제 확인도 바로 이어진 같은 문의의 최종 답변으로만 해소한다. */
final class ChatEndSummaryTopicResolution {
    private ChatEndSummaryTopicResolution() {}

    static boolean isResolved(ChatEndSummaryData.Input input, long messageId) {
        var current = input.messages().stream().filter(message -> message.id() == messageId
                && message.role().equals("USER")).findFirst().orElse(null);
        while (isValidUser(current)) {
            var asked = findClarification(input, current);
            if (asked == null) {
                return false;
            }
            var next = input.messages().stream().filter(message -> message.role().equals("USER")
                    && message.sequence() > asked.sequence() && !ChatContextFormatter.isSocial(message.content()))
                    .min(Comparator.comparingInt(ChatEndSummaryData.Message::sequence)).orElse(null);
            if (!isValidUser(next)
                    || !ChatEndSummaryInquiryLinker.hasSameClarificationScope(current.content(), next.content())) {
                return false;
            }
            // 저장된 상담 상태가 없는 동일한 일반 주제 확인만 이어서 검사한다.
            if (ChatEndSummaryGeneralTopic.isSameGeneralTopic(current.content(), next.content())
                    && !hasConsultState(input, next.id())) {
                current = next;
                continue;
            }
            return hasSameTopic(input, current, next) && ChatEndSummaryResolution.isResolved(input, next.id());
        }
        return false;
    }

    private static ChatEndSummaryData.Message findClarification(ChatEndSummaryData.Input input,
            ChatEndSummaryData.Message original) {
        var asked = input.messages().stream().filter(message -> message.role().equals("ASSISTANT")
                && Long.valueOf(original.id()).equals(message.replyTo()))
                .max(Comparator.comparingInt(ChatEndSummaryData.Message::sequence)).orElse(null);
        if (asked == null || asked.sequence() <= original.sequence() || !asked.type().equals("CLARIFICATION")
                || !ChatTopicClarification.isMessage(asked.content()) || Set.of("FAILED", "CANCELLED")
                        .contains(asked.executionStatus() == null ? "" : asked.executionStatus())) {
            return null;
        }
        return asked;
    }

    private static boolean hasConsultState(ChatEndSummaryData.Input input, long messageId) {
        var questions = input.messages().stream().filter(message -> Long.valueOf(messageId).equals(message.replyTo()))
                .map(ChatEndSummaryData.Message::id).collect(java.util.stream.Collectors.toSet());
        return input.requests().stream().anyMatch(request -> request.originMessageId() == messageId)
                || input.conditions().stream().anyMatch(condition -> condition.originMessageId() == messageId
                        || Long.valueOf(messageId).equals(condition.answeredMessageId())
                        || questions.contains(condition.askedMessageId()));
    }

    private static boolean isValidUser(ChatEndSummaryData.Message message) {
        return message != null && message.role().equals("USER") && !Set.of("FAILED", "CANCELLED")
                .contains(message.executionStatus() == null ? "" : message.executionStatus());
    }

    private static boolean hasSameTopic(ChatEndSummaryData.Input input, ChatEndSummaryData.Message original,
            ChatEndSummaryData.Message next) {
        String after = ChatEndSummaryInquiryClassifier.classifyMessage(input, next.id());
        if (after.equals("UNKNOWN")) {
            after = ChatEndSummaryInquiryLinker.identifyPurpose(next.content());
        }
        return ChatEndSummaryGeneralTopic.accepts(original.content(), after, next.content());
    }
}
