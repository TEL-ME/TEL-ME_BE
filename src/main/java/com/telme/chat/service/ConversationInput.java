package com.telme.chat.service;

import java.util.regex.Pattern;

/** 원문 저장과 별개로 기본 대화·상담 판별에 쓰는 입력을 정리한다. */
public final class ConversationInput {
    // 숫자·통화·수학 기호 전체를 지우지 않고 장식 이모지 범위만 제외한다.
    private static final String PICTOGRAPH =
            "[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{1F1E6}-\\x{1F1FF}]"
                    + "[\\x{FE0E}\\x{FE0F}]?";
    private static final Pattern EMOJI = Pattern.compile(
            PICTOGRAPH + "(?:\\x{200D}" + PICTOGRAPH + ")*");
    private static final Pattern KEYCAP = Pattern.compile("([0-9#*])\\x{FE0F}?\\x{20E3}");
    private static final Pattern LEADING_TAG = Pattern.compile("^(?:\\[(?:문의|질문)\\]\\s*)+");

    private ConversationInput() {}

    public static String normalize(String content) {
        if (content == null) return null;
        String text = KEYCAP.matcher(content).replaceAll("$1");
        text = EMOJI.matcher(text).replaceAll("").strip();
        return withoutLeadingTag(text);
    }

    public static String withoutLeadingTag(String content) {
        return content == null ? "" : LEADING_TAG.matcher(content.stripLeading()).replaceFirst("");
    }

    public static boolean hasSemanticContent(String content) {
        String normalized = normalize(content);
        return normalized != null && normalized.codePoints().anyMatch(Character::isLetterOrDigit);
    }
}
