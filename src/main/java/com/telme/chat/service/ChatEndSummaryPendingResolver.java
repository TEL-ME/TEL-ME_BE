package com.telme.chat.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 남은 사유는 생성 문장이 아닌 저장된 상담 상태에서 결정한다. */
final class ChatEndSummaryPendingResolver {
    enum Kind { NEEDS_CONFIRMATION, UNANSWERED, OUT_OF_SCOPE, INTERRUPTED, WAITING_RESPONSE }

    record Pending(String key, long originMessageId, String title, String text, Kind kind) {}

    private ChatEndSummaryPendingResolver() {}

    static List<Pending> resolve(ChatEndSummaryData.Input input, Set<Long> pendingIds) {
        List<Pending> result = new ArrayList<>();
        var origins = ChatEndSummaryResolution.findPendingOrigins(input, pendingIds);
        for (var message : input.messages()) {
            if (!message.role().equals("USER") || !origins.contains(message.id())) {
                continue;
            }
            if (ChatEndSummaryResolution.isResolved(input, message.id())) {
                continue;
            }
            var requests = input.requests().stream()
                    .filter(request -> request.originMessageId() == message.id()).toList();
            var waiting = requests.stream().filter(request -> Set.of("PENDING", "WAITING_CONDITION")
                    .contains(request.status())).toList();
            int start = result.size();
            for (var request : waiting) {
                addConditions(input, request, result);
            }
            String title = createTitle(message.content());
            if (Set.of("FAILED", "CANCELLED").contains(normalizeNullable(message.executionStatus()))) {
                result.add(createPending(message, title, "답변을 마치지 못한 문의: ", Kind.INTERRUPTED));
                continue;
            }
            Set<String> bases = new LinkedHashSet<>();
            var replies = ChatEndSummaryResolution.findLatestReplies(input, message.id());
            replies.stream()
                    .map(ChatEndSummaryData.Message::basis).filter(java.util.Objects::nonNull).forEach(bases::add);
            if (bases.contains("NO_EVIDENCE")) {
                result.add(createPending(message, title, "근거 있는 답변을 확인하지 못한 문의: ", Kind.UNANSWERED));
            } else if (bases.contains("OUT_OF_SCOPE")) {
                result.add(createPending(message, title, "상담 범위에 해당하는 안내를 받지 못한 문의: ", Kind.OUT_OF_SCOPE));
            } else if (result.size() == start) {
                var clarification = replies.stream().filter(answer -> answer.type().equals("CLARIFICATION"))
                        .max(java.util.Comparator.comparingInt(ChatEndSummaryData.Message::sequence));
                if (clarification.isPresent()) {
                    result.add(new Pending(message.id() + ":clarification", message.id(), title,
                            "추가 확인이 필요한 문의: ‘" + message.content() + "’ 확인 요청: ‘"
                                    + clarification.get().content() + "’", Kind.NEEDS_CONFIRMATION));
                } else {
                    result.add(createPending(message, title, "답변 완료 여부가 확인되지 않은 문의: ", Kind.WAITING_RESPONSE));
                }
            }
        }
        return List.copyOf(result);
    }

    private static void addConditions(ChatEndSummaryData.Input input, ChatEndSummaryData.Request request,
            List<Pending> output) {
        var conditions = input.conditions().stream()
                .filter(condition -> condition.originMessageId() == request.originMessageId()
                        && (request.requestId() == null || request.requestId() == condition.requestId())
                        && condition.status().equals("PENDING")).toList();
        if (request.status().equals("PENDING") && conditions.isEmpty() && request.pendingQuestions().isEmpty()) {
            return;
        }
        Set<String> keys = new LinkedHashSet<>();
        conditions.forEach(condition -> keys.add(condition.key()));
        String scope = request.originMessageId() + ":" + (request.requestId() == null
                ? "order-" + request.order() : request.requestId());
        if (keys.contains("location")) {
            boolean store = request.query().contains("매장");
            output.add(new Pending(scope + ":location", request.originMessageId(), store ? "방문 매장 위치" : "지역 확인",
                    store ? "매장 안내를 위한 방문 지역은 아직 확인되지 않았습니다."
                            : "문의에 필요한 지역은 아직 확인되지 않았습니다.", Kind.NEEDS_CONFIRMATION));
        }
        if (keys.contains("agent_visit") || keys.contains("self_visit")) {
            output.add(new Pending(scope + ":visitor", request.originMessageId(), "매장 방문자 구분",
                    "방문자가 본인인지 대리인인지 아직 확인되지 않았습니다.", Kind.NEEDS_CONFIRMATION));
        }
        boolean unknown = keys.stream().anyMatch(key -> !Set.of("location", "agent_visit", "self_visit")
                .contains(key));
        if (keys.isEmpty() || unknown) {
            Set<String> questions = new LinkedHashSet<>();
            request.pendingQuestions().stream().map(ChatEndSummarySourceText::extractClarificationQuestion)
                    .filter(question -> !question.isBlank()).forEach(questions::add);
            int index = 0;
            for (String question : questions) {
                output.add(new Pending(scope + ":question-" + ++index, request.originMessageId(),
                        createTitle(request.query()),
                        "‘" + request.query() + "’ 문의에서 아직 확인되지 않은 내용: ‘" + question + "’",
                        Kind.NEEDS_CONFIRMATION));
            }
            if (questions.isEmpty()) {
                output.add(new Pending(scope + ":waiting", request.originMessageId(), createTitle(request.query()),
                        "추가 확인이 완료되지 않은 문의: ‘" + request.query() + "’", Kind.NEEDS_CONFIRMATION));
            }
        }
    }

    private static Pending createPending(ChatEndSummaryData.Message message, String title, String prefix, Kind kind) {
        return new Pending(message.id() + ":" + kind, message.id(), title,
                prefix + "‘" + message.content() + "’", kind);
    }

    private static String createTitle(String question) {
        return ChatEndSummaryInquiryTitle.create(ChatEndSummaryInquiryLinker.identifyPurpose(question), question);
    }

    private static String normalizeNullable(String value) {
        return value == null ? "" : value;
    }
}
