package com.telme.chat.service;

import com.telme.chat.entity.ChatMessage;
import java.util.List;
import java.util.stream.Collectors;

final class ChatSummaryPrompt {

    static final String SYSTEM_PROMPT = """
            당신은 통신 상담 대화를 다음 상담 턴에서 재사용할 수 있도록 요약합니다.
            확정된 고객 요구와 조건, 안내한 핵심 내용, 아직 해결되지 않은 질문만 남기십시오.
            대화에 없는 내용을 추측하거나 새로 만들지 마십시오.
            간결한 한국어 문장으로 작성하고 요약 외의 설명은 출력하지 마십시오.
            """;

    private ChatSummaryPrompt() {
    }

    static String buildUserPrompt(String previousSummary, List<ChatContextMessage> messages) {
        return """
                [기존 상담 요약]
                %s

                [새로 반영할 대화]
                %s
                """.formatted(
                previousSummary == null ? "없음" : previousSummary,
                messages.stream()
                        .map(ChatSummaryPrompt::formatMessage)
                        .collect(Collectors.joining("\n"))
        ).trim();
    }

    private static String formatMessage(ChatContextMessage message) {
        String speaker = message.role() == ChatMessage.Role.USER ? "고객" : "상담사";
        StringBuilder line = new StringBuilder()
                .append("- ")
                .append(speaker)
                .append("(")
                .append(message.messageType())
                .append("): ");
        if (message.content() != null) {
            line.append(message.content());
        }
        if (message.storeResults() != null) {
            if (message.content() != null) {
                line.append(" | ");
            }
            line.append("매장 결과=").append(message.storeResults());
        }
        return line.toString();
    }
}
