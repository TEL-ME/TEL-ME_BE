package com.telme.intent.service;

// 고객 원문을 보존하면서 한글 띄어쓰기만 라우팅 입력에서 통일한다.
public final class RoutingQuestionNormalizer {
    private RoutingQuestionNormalizer() {}

    public static String normalize(String question) {
        if (question == null || question.isBlank()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        int[] characters = question.strip().codePoints().toArray();
        for (int index = 0; index < characters.length; index++) {
            int current = characters[index];
            if (!isSpace(current)) {
                result.appendCodePoint(current);
                continue;
            }
            int start = index;
            boolean lineBreak = current == '\n' || current == '\r';
            while (index + 1 < characters.length && isSpace(characters[index + 1])) {
                index++;
                lineBreak |= characters[index] == '\n' || characters[index] == '\r';
            }
            if (start == 0 || index + 1 == characters.length) {
                continue;
            }
            // 줄바꿈은 독립 요청의 경계이며, 숫자와 영문 사이 공백은 값의 일부일 수 있다.
            if (lineBreak) {
                result.append('\n');
            } else if (!isHangul(characters[start - 1]) && !isHangul(characters[index + 1])) {
                result.append(' ');
            }
        }
        return result.toString();
    }

    static String quoteKey(String text) {
        if (text == null) {
            return "";
        }
        return normalize(text).strip();
    }

    private static boolean isSpace(int character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character);
    }

    private static boolean isHangul(int character) {
        Character.UnicodeScript script = Character.UnicodeScript.of(character);
        return script == Character.UnicodeScript.HANGUL;
    }
}
