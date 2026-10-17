package com.telme.chat.service;

import com.telme.chat.converter.ChatContextFormatter;
import java.util.Comparator;
import java.util.Set;
import java.util.regex.Pattern;

/** 상담 요청이 없는 주제 확인도 바로 이어진 같은 문의의 최종 답변으로만 해소한다. */
final class ChatEndSummaryTopicResolution {
    private static final Pattern GENERAL_TOPIC = Pattern.compile(
            "^(요금제|요금|유심|로밍|해지|매장)(?:은|는|을|가|에대해|에대해서)?(?:좀)?"
                    + "(?:알려(?:줘|줘요|주세요|줄래|줄래요|주실래요)|안내(?:해줘|해주세요)"
                    + "|문의(?:할게요|합니다)|궁금(?:해|해요))?[.!?]*$");

    private ChatEndSummaryTopicResolution() {}

    static boolean isResolved(ChatEndSummaryData.Input input, long messageId) {
        var original = input.messages().stream().filter(message -> message.id() == messageId
                && message.role().equals("USER")).findFirst().orElse(null);
        if (original == null || Set.of("FAILED", "CANCELLED")
                .contains(original.executionStatus() == null ? "" : original.executionStatus())) {
            return false;
        }
        var asked = input.messages().stream().filter(message -> message.role().equals("ASSISTANT")
                && Long.valueOf(messageId).equals(message.replyTo()))
                .max(Comparator.comparingInt(ChatEndSummaryData.Message::sequence)).orElse(null);
        if (asked == null || asked.sequence() <= original.sequence() || !asked.type().equals("CLARIFICATION")
                || !ChatTopicClarification.isMessage(asked.content())) {
            return false;
        }
        var next = input.messages().stream().filter(message -> message.role().equals("USER")
                && message.sequence() > asked.sequence() && !ChatContextFormatter.isSocial(message.content()))
                .min(Comparator.comparingInt(ChatEndSummaryData.Message::sequence)).orElse(null);
        // 다른 문의를 건너뛰고 뒤의 정상 답변에 연결하지 않는다.
        if (next == null || !ChatEndSummaryInquiryLinker.hasSameClarificationScope(original.content(), next.content())
                || !hasSameTopic(input, original, next)) {
            return false;
        }
        return ChatEndSummaryResolution.isResolved(input, next.id());
    }

    private static boolean hasSameTopic(ChatEndSummaryData.Input input, ChatEndSummaryData.Message original,
            ChatEndSummaryData.Message next) {
        String question = ConsultationQuestion.extract(original.content());
        var topic = GENERAL_TOPIC.matcher(ChatEndSummaryInquiryLinker.normalizeText(question));
        // 세부 질문에 대한 답변을 대분류가 같다는 이유만으로 대신 해결 처리하지 않는다.
        if (!topic.matches()) {
            return false;
        }
        String after = ChatEndSummaryInquiryClassifier.classifyMessage(input, next.id());
        if (after.equals("UNKNOWN")) {
            after = ChatEndSummaryInquiryLinker.identifyPurpose(next.content());
        }
        return switch (topic.group(1)) {
            case "요금" -> Set.of("PLAN", "BILLING").contains(after);
            case "요금제" -> after.equals("PLAN");
            case "유심" -> after.equals("USIM");
            case "로밍" -> after.equals("ROAMING");
            case "해지" -> after.equals("CANCELLATION");
            case "매장" -> after.equals("STORE");
            default -> false;
        };
    }
}
