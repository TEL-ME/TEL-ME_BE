package com.telme.chat.service;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 원문을 수정하지 않고, 독립적인 인사·호칭·감사만 상담 분석에서 제외한다. */
public final class ConsultationQuestion {
    private static final String SPACE = "[\\p{javaWhitespace}\\p{Zs}]*";
    private static final String PHRASE = BasicConversationClassifier.consultationPhrases().stream()
            .map(phrase -> phrase.codePoints().mapToObj(code -> Pattern.quote(Character.toString(code)))
                    .collect(Collectors.joining(SPACE)))
            .collect(Collectors.joining("|", "(?:", ")"));
    private static final Pattern CONVERSATION = Pattern.compile(
            "(?<![\\p{L}\\p{N}_])" + PHRASE + "(?:" + SPACE + PHRASE + ")*"
                    + "(?=$|[\\p{javaWhitespace}\\p{Zs},，.!！?？:：;；~～…])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Map<Character, Character> QUOTES = Map.of(
            '\"', '\"', '\'', '\'', '“', '”', '‘', '’', '「', '」', '『', '』');
    private static final Pattern LITERAL_DESCRIPTION = Pattern.compile(
            "^(?:\\.[A-Za-z0-9]|[\\s,，]*(?:문자|메시지|인사|단어|표현|문구|뜻|라는|이라고|라고))");

    private static final Pattern LITERAL_PREFIX = Pattern.compile(
            "(?:보낼\\s*내용|(?:문자|메시지)\\s*(?:본문|내용)|본문|문구|내용)"
                    + "(?:은|는|이|가)?\\s*[:：=]?\\s*$");

    private ConsultationQuestion() {}

    public static String extract(String original) {
        return analyze(original).question();
    }

    public static Analysis analyze(String original) {
        if (original == null || original.isBlank()) return new Analysis(original, false);
        String question = ConversationInput.normalize(original);
        var basic = BasicConversationClassifier.classify(question);
        if (basic.isPure()) return new Analysis(question, basic.hasGreeting());
        boolean greeting = false;
        boolean changed;
        do {
            changed = false;
            var matcher = CONVERSATION.matcher(question);
            while (matcher.find()) {
                if (isQuoted(question, matcher.start(), matcher.end())) continue;
                String before = question.substring(0, matcher.start());
                String after = question.substring(matcher.end());
                if (LITERAL_DESCRIPTION.matcher(after).find() || LITERAL_PREFIX.matcher(before).find()) continue;
                boolean leading = onlySeparators(before);
                boolean trailing = onlySeparators(after);
                boolean isolated = endsClause(before) && startsClause(after);
                if (!leading && !trailing && !isolated) continue;
                // 콜론 뒤의 인사는 전송할 문자 본문일 수 있으므로 지우지 않는다.
                if (!leading && before.stripTrailing().endsWith(":")) continue;
                if (!leading && before.stripTrailing().endsWith("：")) continue;
                String remaining = leading ? trimLeadingSeparators(after)
                        : trailing ? trimTrailingConnectors(before) : before + trimLeadingSeparators(after);
                // 기본 대화나 애매한 입력을 빈 상담 질문으로 바꾸지 않는다.
                if (remaining.isBlank()) return new Analysis(ConversationInput.normalize(original), basic.hasGreeting());
                greeting |= BasicConversationClassifier.classify(matcher.group()).hasGreeting();
                question = remaining.strip();
                changed = true;
                break;
            }
        } while (changed);
        return new Analysis(question, greeting);
    }

    private static boolean isQuoted(String text, int start, int end) {
        Character closing = null;
        for (int index = 0; index < end; index++) {
            char character = text.charAt(index);
            if (closing != null) {
                if (index >= start) return true;
                if (character == closing) closing = null;
            } else if (QUOTES.containsKey(character)) {
                closing = QUOTES.get(character);
                if (index >= start) return true;
            }
        }
        return false;
    }

    private static boolean onlySeparators(String text) {
        return text.codePoints().allMatch(ConsultationQuestion::isSeparator);
    }

    private static String trimLeadingSeparators(String text) {
        int index = 0;
        while (index < text.length() && isSeparator(text.codePointAt(index))) {
            // .5GB처럼 숫자의 일부인 점은 인사의 구분자가 아니다.
            if (text.charAt(index) == '.' && index + 1 < text.length()
                    && Character.isDigit(text.charAt(index + 1))) break;
            index += Character.charCount(text.codePointAt(index));
        }
        return text.substring(index);
    }

    private static String trimTrailingConnectors(String text) {
        String stripped = text.stripTrailing();
        int end = stripped.length();
        while (end > 0) {
            int code = stripped.codePointBefore(end);
            if (!Character.isWhitespace(code) && !Character.isSpaceChar(code)
                    && ",，:：;；".indexOf(code) < 0) break;
            end -= Character.charCount(code);
        }
        return stripped.substring(0, end);
    }

    private static boolean endsClause(String text) {
        String stripped = text.stripTrailing();
        return (!stripped.isEmpty() && isClauseSeparator(stripped.codePointBefore(stripped.length())))
                || text.endsWith("\n") || text.endsWith("\r");
    }

    private static boolean startsClause(String text) {
        String stripped = text.stripLeading();
        return (!stripped.isEmpty() && isClauseSeparator(stripped.codePointAt(0)))
                || text.startsWith("\n") || text.startsWith("\r");
    }

    private static boolean isSeparator(int character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character)
                || isClauseSeparator(character);
    }

    private static boolean isClauseSeparator(int character) {
        return ",，.!！?？:：;；~～…".indexOf(character) >= 0;
    }

    public record Analysis(String question, boolean hasGreeting) {}
}
