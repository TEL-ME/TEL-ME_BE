package com.telme.chat.service;

import com.telme.chat.entity.ChatMessage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

/** 동일 유심 문의의 명시적인 재질문만 고객 원문에 연결한다. 정책이나 조건을 새로 만들지 않는다. */
public final class ChatRepeatedInquiryResolver {
    private static final Pattern USIM = Pattern.compile("유심|usim", Pattern.CASE_INSENSITIVE);
    private static final Pattern REPEAT = Pattern.compile("(?:같은|동일한)\\s*(?:유심|usim)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTINUE = Pattern.compile("다시|이어서|재차|추가로");
    private static final Pattern DIFFERENT_TARGET = Pattern.compile(
            "어머니|아버지|엄마|아빠|부모|배우자|자녀|대리인|명의|회선|전화번호|"
                    + "다른|별도|새로운|esim|이심|e심|로밍|요금제|인터넷|청구|납부|번호이동",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OPERATION = Pattern.compile("재발급|교체|정지|해지|개통|변경");

    private ChatRepeatedInquiryResolver() {}

    public static boolean isVerifiedRepeat(String question, String resolved, ChatContext context) {
        var result = resolve(question, context);
        return result != null && !result.needsClarification() && result.question().equals(resolved);
    }

    static ChatQuestionResolver.Resolution resolve(String question, ChatContext context) {
        if (context == null || !isRepeat(question) || DIFFERENT_TARGET.matcher(question).find()) {
            return null;
        }
        var previous = new LinkedHashMap<Long, ChatContextMessage>();
        var messages = new ArrayList<>(context.summarySources());
        messages.addAll(context.history());
        for (var message : messages) {
            if (message.role() == ChatMessage.Role.USER && message.content() != null
                    && !com.telme.chat.converter.ChatContextFormatter.isSocial(message.content())) {
                var duplicate = previous.putIfAbsent(message.messageId(), message);
                if (duplicate != null && !duplicate.equals(message)) {
                    return new ChatQuestionResolver.Resolution(question, true, List.of());
                }
            }
        }
        var ordered = previous.values().stream()
                .sorted(Comparator.comparingInt(ChatContextMessage::sequenceNo)).toList();
        var roots = ordered.stream().filter(message -> USIM.matcher(message.content()).find())
                .filter(message -> !isRepeat(message.content())).toList();
        // 독립 유심 문의가 둘이거나 다른 대상이 있으면 최근 발언이라는 이유로 하나를 고르지 않는다.
        if (roots.size() != 1 || DIFFERENT_TARGET.matcher(roots.getFirst().content()).find()) {
            return new ChatQuestionResolver.Resolution(question, true, List.of());
        }
        int start = ordered.indexOf(roots.getFirst());
        var chain = ordered.subList(start, ordered.size());
        if (chain.stream().anyMatch(message -> DIFFERENT_TARGET.matcher(message.content()).find()
                || message.messageId() != roots.getFirst().messageId() && !isRepeat(message.content()))
                || !hasSameOperation(question, chain)) {
            return new ChatQuestionResolver.Resolution(question, true, List.of());
        }
        String antecedents = chain.stream().map(ChatContextMessage::content)
                .collect(java.util.stream.Collectors.joining("\n"));
        return new ChatQuestionResolver.Resolution("[대상을 확인할 이전 고객 발언]\n" + antecedents
                + "\n[현재 후속 질문]\n" + question, false,
                chain.stream().map(ChatContextMessage::messageId).toList());
    }

    private static boolean isRepeat(String question) {
        return REPEAT.matcher(question).find() && CONTINUE.matcher(question).find();
    }

    private static boolean hasSameOperation(String question, List<ChatContextMessage> chain) {
        var operations = new java.util.LinkedHashSet<String>();
        for (String text : java.util.stream.Stream.concat(chain.stream().map(ChatContextMessage::content),
                java.util.stream.Stream.of(question)).toList()) {
            OPERATION.matcher(text).results().map(java.util.regex.MatchResult::group).forEach(operations::add);
        }
        return operations.size() <= 1;
    }
}
