package com.telme.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ChatEndSummaryInquiryGrouper {
    record Inquiry(String key, String title, String sourceText, String periodContext,
            List<Long> sourceMessageIds, List<Long> questionMessageIds, int sourceItems) {
        String protectedText() {
            return periodContext.isBlank() ? sourceText : periodContext + " " + sourceText;
        }
    }

    private ChatEndSummaryInquiryGrouper() {}

    static List<Inquiry> group(ChatEndSummaryData.Input input, JsonNode summary) {
        Map<Long, ChatEndSummaryData.Message> messages = new LinkedHashMap<>();
        input.messages().forEach(message -> messages.put(message.id(), message));
        Map<Long, Long> roots = ChatEndSummaryInquiryLinker.roots(input);
        Map<String, Builder> groups = new LinkedHashMap<>();
        int unknown = 0;
        for (var item : summary.path("guidance")) {
            String text = stripLabel(item.path("text").asText());
            List<Long> ids = ids(item);
            Map<Long, List<Long>> scopedSources = new LinkedHashMap<>();
            for (long id : ids) {
                var answer = messages.get(id);
                if (answer != null && answer.role().equals("ASSISTANT") && answer.replyTo() != null) {
                    long root = roots.getOrDefault(answer.replyTo(), answer.replyTo());
                    scopedSources.computeIfAbsent(root, ignored -> new ArrayList<>()).add(id);
                }
            }
            // 발췌 단계에서 동일 원문이 합쳐졌어도 사람·회선별 출처는 다시 나눈다.
            if (scopedSources.isEmpty()) {
                addGuidance(input, messages, roots, groups, text, ids, null, ++unknown);
            } else {
                for (var scoped : scopedSources.entrySet()) {
                    var answer = messages.get(scoped.getValue().getFirst());
                    var sources = new ArrayList<>(scoped.getValue());
                    ids.stream().filter(id -> messages.containsKey(id) && messages.get(id).role().equals("USER")
                            && roots.getOrDefault(id, id).equals(scoped.getKey())).forEach(sources::add);
                    addGuidance(input, messages, roots, groups, text, sources, answer.replyTo(), ++unknown);
                }
            }
        }
        for (var message : input.messages()) {
            if (!message.role().equals("USER")) {
                continue;
            }
            var lastReply = input.messages().stream()
                    .filter(answer -> Long.valueOf(message.id()).equals(answer.replyTo()))
                    .max(java.util.Comparator.comparingInt(ChatEndSummaryData.Message::sequence)).orElse(null);
            if (lastReply != null && (lastReply.type().equals("CLARIFICATION")
                    || Set.of("NO_EVIDENCE", "OUT_OF_SCOPE").contains(
                            lastReply.basis() == null ? "" : lastReply.basis()))) {
                continue;
            }
            long root = roots.getOrDefault(message.id(), message.id());
            for (var group : groups.values()) {
                if (group.origins.contains(root)) {
                    group.questions.add(message.content());
                    group.questionSources.add(message.id());
                }
            }
        }
        for (var topic : summary.path("topics")) {
            String prefix = "고객이 언급한 기간(원문): ";
            if (!topic.path("text").asText().startsWith(prefix)) {
                continue;
            }
            List<Long> topicIds = ids(topic);
            var targets = groups.values().stream().filter(group -> topicIds.stream()
                    .map(id -> roots.getOrDefault(id, id)).anyMatch(group.origins::contains)).toList();
            // 한 사용자 발언이 여러 문의를 포함하면 기간을 임의의 상품에 붙이지 않는다.
            if (targets.size() == 1) {
                var target = targets.getFirst();
                target.periods.add(topic.path("text").asText().substring(prefix.length()));
                target.sources.addAll(topicIds);
            }
        }
        return groups.values().stream().map(Builder::build).toList();
    }

    private static void addGuidance(ChatEndSummaryData.Input input,
            Map<Long, ChatEndSummaryData.Message> messages, Map<Long, Long> roots, Map<String, Builder> groups,
            String text, List<Long> ids, Long origin, int unknown) {
        long root = origin == null ? -1 : roots.getOrDefault(origin, origin);
        String purpose = ChatEndSummaryInquiryLinker.purpose(text);
        if (purpose.equals("UNKNOWN") && origin != null) {
            purpose = input.requests().stream().filter(request -> request.originMessageId() == origin)
                    .map(ChatEndSummaryData.Request::query).map(ChatEndSummaryInquiryLinker::purpose)
                    .filter(value -> !value.equals("UNKNOWN")).distinct().reduce((a, b) -> "UNKNOWN")
                    .orElse("UNKNOWN");
        }
        if (purpose.equals("UNKNOWN") && messages.containsKey(root)) {
            purpose = ChatEndSummaryInquiryLinker.purpose(messages.get(root).content());
        }
        String key = root + ":" + purpose;
        if (root < 0 || purpose.equals("UNKNOWN")) {
            key += ":" + unknown;
        }
        var group = groups.computeIfAbsent(key, ignored -> new Builder());
        group.key = key;
        group.purpose = purpose;
        group.origins.add(root);
        group.origins.add(origin == null ? root : origin);
        group.texts.add(text);
        group.sources.addAll(ids);
    }

    static String stripLabel(String text) {
        return text.replaceFirst("^(?:안내받은 내용|조건 확인 전 안내|조건 확인 후 안내|상담 정리):\\s*", "");
    }

    private static List<Long> ids(JsonNode item) {
        List<Long> ids = new ArrayList<>();
        item.path("sourceMessageIds").forEach(value -> ids.add(value.asLong()));
        return ids;
    }

    private static final class Builder {
        private String key;
        private String purpose;
        private final Set<String> questions = new LinkedHashSet<>();
        private final Set<Long> questionSources = new LinkedHashSet<>();
        private final List<String> texts = new ArrayList<>();
        private final Set<Long> sources = new LinkedHashSet<>();
        private final Set<Long> origins = new LinkedHashSet<>();
        private final Set<String> periods = new LinkedHashSet<>();

        private Inquiry build() {
            String last = texts.getLast();
            boolean supersedes = purpose.equals("PLAN") && texts.size() > 1 && last.matches(
                    "(?s).*이전 안내.*(?:적용되지 않|적용할 수 없|취소).*");
            // 문단 일부만 달라도 동일 문장은 반복하지 않는다. 조건이 다른 문장은 그대로 남긴다.
            String source = supersedes ? last : ChatEndSummarySourceText.deduplicate(texts);
            return new Inquiry(key, ChatEndSummaryInquiryTitle.create(purpose, String.join(" ", questions)),
                    source, String.join(" ", periods),
                    List.copyOf(sources), List.copyOf(questionSources), texts.size());
        }
    }
}
