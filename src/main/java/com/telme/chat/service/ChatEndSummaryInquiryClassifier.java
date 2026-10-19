package com.telme.chat.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 저장된 업무와 근거 분류를 우선하며, 여러 카테고리 중 하나를 임의로 선택하지 않는다. */
final class ChatEndSummaryInquiryClassifier {
    private ChatEndSummaryInquiryClassifier() {}

    static String classifyMessage(ChatEndSummaryData.Input input, long messageId) {
        var message = input.messages().stream().filter(value -> value.id() == messageId).findFirst();
        if (message.isEmpty()) {
            return "UNKNOWN";
        }
        var ids = input.messages().stream()
                .filter(value -> value.id() == messageId || Long.valueOf(messageId).equals(value.replyTo()))
                .map(ChatEndSummaryData.Message::id).toList();
        return classifySources(input, ids);
    }

    static String classifySources(ChatEndSummaryData.Input input, List<Long> ids) {
        Set<String> intents = new LinkedHashSet<>();
        for (long id : ids) {
            var value = input.inquiryClassifications().get(id);
            if (value != null && !value.intent().isBlank()) {
                intents.add(value.intent());
            }
        }
        if (intents.equals(Set.of("STORE"))) {
            return "STORE";
        }
        Set<String> categories = findCategories(input, ids);
        return categories.size() == 1 ? categories.iterator().next() : "UNKNOWN";
    }

    static boolean hasAmbiguousCategories(ChatEndSummaryData.Input input, List<Long> ids) {
        return findCategories(input, ids).size() > 1;
    }

    private static Set<String> findCategories(ChatEndSummaryData.Input input, List<Long> ids) {
        Set<String> categories = new LinkedHashSet<>();
        for (long id : ids) {
            var value = input.inquiryClassifications().get(id);
            if (value == null) {
                continue;
            }
            for (String category : value.faqCategories()) {
                String purpose = switch (category) {
                    case "TERMINATE" -> "CANCELLATION";
                    case "BILLING", "PLAN", "USIM", "SUBSCRIBE", "PORTING", "NAME_CHANGE",
                            "DEVICE", "ROAMING", "SERVICE" -> category;
                    default -> "UNKNOWN";
                };
                categories.add(purpose);
            }
        }
        return categories;
    }
}
