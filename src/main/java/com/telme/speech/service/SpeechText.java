package com.telme.speech.service;

import java.util.regex.Pattern;

// 화면용 답변을 읽기 좋은 문장으로 바꾼다. 링크 주소와 목록 기호는 소리로 읽으면 의미가 없어 뺀다
final class SpeechText {

    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern LIST_MARKER = Pattern.compile("(?m)^\\s*[-•·*]\\s+");
    private static final Pattern MARKUP = Pattern.compile("[*#`]");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final String SENTENCE_ENDS = ".?!。";

    private SpeechText() {
    }

    static String toSpeakable(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String text = URL.matcher(content).replaceAll(" ");
        text = LIST_MARKER.matcher(text).replaceAll("");
        text = MARKUP.matcher(text).replaceAll("");
        text = WHITESPACE.matcher(text).replaceAll(" ").strip();
        return truncate(text, maxLength);
    }

    // 문장 중간에서 끊기지 않도록 한도 안의 마지막 문장 끝에서 자르고, 앞쪽 절반 안에 문장 끝이 없으면 한도에서 자른다
    private static String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        for (int i = maxLength - 1; i >= maxLength / 2; i--) {
            if (SENTENCE_ENDS.indexOf(text.charAt(i)) >= 0) {
                return text.substring(0, i + 1);
            }
        }
        return text.substring(0, maxLength).strip();
    }
}
