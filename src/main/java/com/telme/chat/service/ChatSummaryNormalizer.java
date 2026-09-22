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
        return normalized
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .trim();
    }
}
