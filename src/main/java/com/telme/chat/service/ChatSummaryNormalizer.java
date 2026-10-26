package com.telme.chat.service;

import static com.telme.chat.guard.MaskSpan.MASK;

import com.telme.chat.safety.OutputMaskContext;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
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
    private static final Pattern EMPHASIS_MARKER = Pattern.compile("\\*{2,}");
    private static final Pattern UNVERIFIED_MASK = Pattern.compile("\\*{3,}");
    private static final Pattern MEANINGFUL_TEXT = Pattern.compile("[\\p{L}\\p{N}]");

    private ChatSummaryNormalizer() {
    }

    static String normalize(String value, OutputMaskContext masks) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (CONVERSATION_DATA_TAG.matcher(value).find()
                || CONVERSATION_ROLE_LINE.matcher(value).find()) {
            return null;
        }

        // 형식 정리용 인용 확인이며 출력 욕설 검사의 허용 경계와는 별도로 유지한다.
        String marker = "telmeMask" + UUID.randomUUID().toString().replace("-", "");
        Map<String, String> unverifiedMasks = new LinkedHashMap<>();
        String protectedMasks = preserveMasks(value, masks, marker);
        String candidate = extractSummaryBlock(preserveUnverifiedMasks(protectedMasks, unverifiedMasks, marker));
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
        for (var entry : unverifiedMasks.entrySet()) {
            normalized = normalized.replace(entry.getKey(), entry.getValue());
        }
        normalized = normalized.replace(marker, MASK);

        return MEANINGFUL_TEXT.matcher(normalized).find() ? normalized : null;
    }

    private static String preserveUnverifiedMasks(String value, Map<String, String> preserved, String marker) {
        // 문구가 바뀐 실제 마스크일 수 있으므로 출처 불일치만으로 별표를 삭제하거나 합치지 않는다.
        // 표시 보존이며 출력 검사에 사용할 신뢰 출처는 추가하지 않는다.
        return UNVERIFIED_MASK.matcher(value).replaceAll(match -> {
            String token = marker + "literal" + preserved.size() + "End";
            preserved.put(token, match.group());
            return token;
        });
    }

    private static String preserveMasks(String value, OutputMaskContext masks, String marker) {
        Set<Integer> positions = new TreeSet<>();
        for (var source : masks.sources()) {
            for (var span : source.spans()) {
                int left = span.start();
                while (left > 0 && whitespace(source.content().charAt(left - 1))) {
                    left--;
                }
                while (left > 0 && !whitespace(source.content().charAt(left - 1))) {
                    left--;
                }
                int right = span.end();
                while (right < source.content().length() && whitespace(source.content().charAt(right))) {
                    right++;
                }
                while (right < source.content().length() && !whitespace(source.content().charAt(right))) {
                    right++;
                }
                String quote = source.content().substring(left, right);
                if (quote.replace(MASK, "").isBlank()) {
                    continue;
                }
                for (int offset = value.indexOf(quote); offset >= 0; offset = value.indexOf(quote, offset + 1)) {
                    positions.add(offset + span.start() - left);
                }
            }
        }
        StringBuilder protectedValue = new StringBuilder();
        int cursor = 0;
        for (int position : positions) {
            if (position < cursor) {
                continue;
            }
            protectedValue.append(value, cursor, position).append(marker);
            cursor = position + MASK.length();
        }
        return protectedValue.append(value, cursor, value.length()).toString();
    }

    private static boolean whitespace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }

    private static String extractSummaryBlock(String value) {
        Matcher matcher = SUMMARY_BLOCK.matcher(value);
        return matcher.find() ? matcher.group(1) : value;
    }

    private static String removeMarkdown(String line) {
        String normalized = HEADING.matcher(line).replaceFirst("");
        normalized = LIST_MARKER.matcher(normalized).replaceFirst("");
        normalized = MARKDOWN_LINK.matcher(normalized).replaceAll("$1");
        return EMPHASIS_MARKER.matcher(normalized).replaceAll("")
                .replace("__", "")
                .replace("`", "")
                .trim();
    }
}
