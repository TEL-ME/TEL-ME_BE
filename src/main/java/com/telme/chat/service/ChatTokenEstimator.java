package com.telme.chat.service;

import org.springframework.stereotype.Component;

@Component
public class ChatTokenEstimator {

    private static final int MESSAGE_OVERHEAD_TOKENS = 4;
    private static final int ASCII_CHARACTERS_PER_TOKEN = 4;

    public int estimate(ChatContextMessage message) {
        return MESSAGE_OVERHEAD_TOKENS
                + estimateText(message.content())
                + estimateText(message.storeResults());
    }

    int estimatePromptPart(String value) {
        return value == null || value.isBlank()
                ? 0
                : MESSAGE_OVERHEAD_TOKENS + estimateText(value);
    }

    int estimateText(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        int tokens = 0;
        int asciiRunLength = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (Character.isWhitespace(codePoint)) {
                tokens += asciiTokens(asciiRunLength);
                asciiRunLength = 0;
            } else if (codePoint < 128 && Character.isLetterOrDigit(codePoint)) {
                asciiRunLength++;
            } else {
                tokens += asciiTokens(asciiRunLength) + 1;
                asciiRunLength = 0;
            }
        }
        return tokens + asciiTokens(asciiRunLength);
    }

    private int asciiTokens(int length) {
        return (length + ASCII_CHARACTERS_PER_TOKEN - 1) / ASCII_CHARACTERS_PER_TOKEN;
    }
}
