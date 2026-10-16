package com.telme.chat.service;

import java.util.ArrayList;
import java.util.List;

/** 모델에 보낼 구간만 나누며 고객용 문단은 별도로 보관한 원문에서 복원한다. */
final class ChatEndSummaryGuidanceSplitter {
    private ChatEndSummaryGuidanceSplitter() {}

    static List<String> split(String text, int limit) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + limit, text.length());
            if (end < text.length()) {
                if (Character.isHighSurrogate(text.charAt(end - 1))) {
                    end--;
                }
                int boundary = findBoundary(text, start, end);
                if (boundary > start) {
                    end = boundary;
                }
            }
            String part = text.substring(start, end).strip();
            if (!part.isEmpty()) {
                result.add(part);
            }
            start = end;
            while (start < text.length() && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
        }
        return List.copyOf(result);
    }

    private static int findBoundary(String text, int start, int end) {
        int whitespace = -1;
        for (int index = end - 1; index > start; index--) {
            if (!Character.isWhitespace(text.charAt(index))) {
                continue;
            }
            if (whitespace < 0) {
                whitespace = index;
            }
            if (".!?".indexOf(text.charAt(index - 1)) >= 0) {
                return index;
            }
        }
        return whitespace;
    }
}
