package com.telme.chat.service;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class ChatSummaryNormalizer {

    private static final Pattern XML_OR_HTML_TAG = Pattern.compile(
            "</?[A-Za-z][A-Za-z0-9_-]*(?:\\s+[^<>]*)?/?>",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SUMMARY_BLOCK = Pattern.compile(
            "<summary(?:\\s+[^<>]*)?>\\s*(.*?)\\s*</summary\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern CONVERSATION_DATA_TAG = Pattern.compile(
            "(?:<|&lt;)\\s*/?\\s*conversation_data(?:\\s|>|&gt;)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONVERSATION_ROLE_LINE = Pattern.compile(
            "(?m)^\\s*(?:[-*+]\\s*)?(?:고객|상담사)\\s*\\([A-Z_]+\\)\\s*:"
    );
    private static final Pattern CODE_FENCE = Pattern.compile("^```(?:[A-Za-z0-9_-]+)?\\s*$");
    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+");
    private static final Pattern LIST_MARKER = Pattern.compile("^(?:[-*+]\\s+|\\d+[.)]\\s+|>\\s*)");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^]\\r\\n]+)]\\([^)]*\\)");
    // 고정 민감정보 마스크(***)는 보존하고 일반 강조용 별표 두 개만 제거한다.
    private static final Pattern BOLD_MARKER = Pattern.compile("(?<!\\*)\\*\\*(?!\\*)");
    // 마스크에 강조 기호가 붙은 5개·7개 별표를 보존한다. 인접한 두 마스크(6개)는 합치지 않는다.
    private static final Pattern EMPHASIZED_MASK = Pattern.compile("(?<!\\*)\\*{5}(?:\\*{2})?(?!\\*)");
    private static final Pattern MEANINGFUL_TEXT = Pattern.compile("[\\p{L}\\p{N}]");

    private ChatSummaryNormalizer() {
    }

    static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (CONVERSATION_DATA_TAG.matcher(value).find()
                || CONVERSATION_ROLE_LINE.matcher(value).find()) {
            return null;
        }

        String candidate = extractSummaryBlock(value);
        String withoutTags = XML_OR_HTML_TAG.matcher(candidate).replaceAll(" ");
        String normalized = Arrays.stream(withoutTags.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .filter(line -> !CODE_FENCE.matcher(line).matches())
                .map(ChatSummaryNormalizer::removeMarkdown)
                .filter(line -> !line.isBlank())
                .collect(Collectors.joining(" "))
                .replaceAll("\\s+", " ")
                .trim();

        return MEANINGFUL_TEXT.matcher(normalized).find() ? normalized : null;
    }

    private static String extractSummaryBlock(String value) {
        Matcher matcher = SUMMARY_BLOCK.matcher(value);
        return matcher.find() ? matcher.group(1) : value;
    }

    private static String removeMarkdown(String line) {
        String normalized = HEADING.matcher(line).replaceFirst("");
        normalized = LIST_MARKER.matcher(normalized).replaceFirst("");
        normalized = MARKDOWN_LINK.matcher(normalized).replaceAll("$1");
        normalized = EMPHASIZED_MASK.matcher(normalized).replaceAll("***");
        return BOLD_MARKER.matcher(normalized).replaceAll("")
                .replace("__", "")
                .replace("`", "")
                .trim();
    }
}
