package com.telme.chat.converter;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatContext;
import com.telme.chat.service.ChatContextMessage;
import com.telme.chat.service.ChatTokenEstimator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChatContextFormatter {
    private ChatContextFormatter() {}

    public static boolean isSocial(String content) {
        if (content == null) return false;
        String normalized = content.strip().replaceAll("(?U)\\s+", "").replaceAll("[.!?~…]+", "");
        return java.util.Set.of("안녕", "안녕하세요", "안녕하십니까", "반가워요", "반갑습니다",
                "감사합니다", "감사해요", "감사", "고마워", "고마워요", "고맙습니다", "고맙네요",
                "안녕히계세요", "안녕히가세요", "잘가요", "잘가", "다음에또올게요", "수고하세요", "수고하셨습니다",
                "천만에요", "천만에요더궁금한점이있으면말씀해주세요",
                "이용해주셔서감사합니다궁금한점이생기면다시말씀해주세요").contains(normalized);
    }

    // 최신 질문과 답변을 함께 남기고, 남는 예산에 상담 요약을 넣는다.
    public static String format(ChatContext context, int budget, ChatTokenEstimator estimator) {
        if (context == null || budget <= 0) {
            return "없음";
        }
        List<String> history = new ArrayList<>();
        int used = 16;
        List<ChatContextMessage> messages = context.history();
        for (int index = messages.size() - 1; index >= 0;) {
            ChatContextMessage latest = messages.get(index);
            String exchange = line(latest);
            int consumed = 1;
            if (latest.role() == ChatMessage.Role.ASSISTANT && index > 0
                    && messages.get(index - 1).role() == ChatMessage.Role.USER) {
                exchange = line(messages.get(index - 1)) + "\n" + exchange;
                consumed = 2;
            }
            int size = estimator.estimatePromptPart(exchange);
            if (used + size > budget) {
                break;
            }
            history.add(exchange);
            used += size;
            index -= consumed;
        }
        Collections.reverse(history);
        // 과거 모델 답변과 출처가 없는 일반 요약을 새 정책 답변의 입력으로 재사용하지 않는다.
        String summary = context.summarySources().stream()
                .filter(message -> message.role() == ChatMessage.Role.USER)
                .map(ChatContextFormatter::line).collect(java.util.stream.Collectors.joining("\n"));
        if (summary.isBlank()) summary = null;
        if (summary != null && used + estimator.estimatePromptPart(summary) > budget) {
            summary = null;
        }
        String result = combine(summary, history);
        if (estimator.estimatePromptPart(result) > budget) {
            summary = null;
            result = combine(null, history);
        }
        while (!history.isEmpty() && estimator.estimatePromptPart(result) > budget) {
            history.removeFirst();
            result = combine(summary, history);
        }
        return result.isBlank() ? "없음" : result;
    }

    private static String combine(String summary, List<String> history) {
        return (summary == null ? "" : "[이전 상담 요약]\n" + summary + "\n")
                + (history.isEmpty() ? "" : "[최근 대화]\n" + String.join("\n", history));
    }

    private static String line(ChatContextMessage message) {
        String role = message.role() == ChatMessage.Role.USER ? "고객" : "상담사(정책 근거 아님)";
        if (message.role() == ChatMessage.Role.ASSISTANT) {
            return role + ": 이전 응답 이력이 있음. 정책은 이번 FAQ 답변에서 확인할 것.";
        }
        return "%s: %s".formatted(role, escape(message.content() == null ? "매장 결과 응답" : message.content()));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
