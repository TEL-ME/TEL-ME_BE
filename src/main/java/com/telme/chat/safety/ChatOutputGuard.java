package com.telme.chat.safety;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.telme.chat.service.ChatAnswer;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** 최종 출력 문구만 검사한다. 사용자 식별자·입력 제재·개인정보 치환과 연결하지 않는다. */
@Component
public final class ChatOutputGuard {

    private static final int MAX_METADATA_DEPTH = 8;
    private static final String DECORATION = "["
            + "ㅋㅎㅠㅜ".codePoints()
                    .mapToObj(value -> Normalizer.normalize(
                            new String(Character.toChars(value)), Normalizer.Form.NFKC))
                    .collect(Collectors.joining())
            + "]{1,12}";
    private static final Pattern SUFFIX = Pattern.compile(
            "^(?:아|야|이야|이냐|이네|이잖아|이다|이라고|이라는|이고|이요|이에요|입니다"
                    + "|은|는|을|를|도|같아|같네|같은|같다|같이|놈|년|새끼|들|임"
                    + "|하네|하냐|하지마|하지|하고|하는|하면|하다|해요|해){0,3}"
                    + "(?:" + DECORATION + ")?(?:$|[^\\p{L}\\p{N}]).*",
            Pattern.DOTALL);
    private static final Pattern JOINED_QUERY = Pattern.compile("^(?:요금|서비스|답변|안내).*$", Pattern.DOTALL);
    private static final Pattern SEPARATED_SUFFIX = Pattern.compile(
            "^(?:아|야|놈|년|새끼|들|같아|같네|같은){0,3}"
                    + "(?:" + DECORATION + ")?(?:$|[^\\p{L}\\p{N}]).*",
            Pattern.DOTALL);

    private final Policy policy;
    private final List<CompiledRule> rules;
    private final ObjectMapper mapper;

    public ChatOutputGuard(ObjectMapper mapper) {
        this.mapper = mapper;
        try (var input = new ClassPathResource("chat/output-guard-rules.json").getInputStream()) {
            policy = mapper.readValue(input, Policy.class);
        } catch (IOException exception) {
            throw new IllegalStateException("챗봇 출력 검사 규칙을 읽을 수 없습니다.");
        }
        if (policy.version() == null || policy.version().isBlank()
                || policy.rules() == null || policy.rules().isEmpty()
                || policy.allowWords() == null || policy.joinedPrefixes() == null
                || policy.allowWords().stream().anyMatch(value -> value == null || value.isBlank())
                || policy.joinedPrefixes().stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalStateException("챗봇 출력 검사 규칙을 확인해 주세요.");
        }
        Set<String> ids = new HashSet<>();
        rules = policy.rules().stream().map(rule -> {
            if (rule.id() == null || rule.id().isBlank() || !ids.add(rule.id())
                    || rule.term() == null || rule.term().isBlank()) {
                throw new IllegalStateException("챗봇 출력 검사 규칙의 식별자와 표현을 확인해 주세요.");
            }
            String term = normalize(rule.term());
            String expression = term.codePoints()
                    .mapToObj(value -> Pattern.quote(new String(Character.toChars(value))))
                    .collect(Collectors.joining("[\\s._*\\-\\d\\u200B-\\u200D\\uFEFF]{0,6}"));
            return new CompiledRule(rule, term, Pattern.compile(expression));
        }).toList();
    }

    public void verify(ChatAnswer answer) {
        verifyText(answer.content(), "content");
        verifyValues(answer.followUps(), "followUps", 0);
        verifyValues(answer.storeResults(), "storeResults", 0);
        if (answer.storeSearchContext() != null) {
            verifyText(answer.storeSearchContext().label(), "storeSearchContext");
        }
    }

    public void verifyClarification(String question, List<String> options) {
        verifyText(question, "clarification");
        verifyValues(options, "clarificationOptions", 0);
    }

    /** 객체가 바뀌더라도 실제로 저장할 직렬화 결과와 검사 대상이 같도록 확인한다. */
    public void verifySerialized(String content, String followUps, String stores, String context) {
        verifyText(content, "content");
        verifyJson(followUps, "followUps");
        verifyJson(stores, "storeResults");
        verifyJson(context, "storeSearchContext");
    }

    public void verifySerializedClarification(String question, String options) {
        verifyText(question, "clarification");
        verifyJson(options, "clarificationOptions");
    }

    private void verifyJson(String value, String field) {
        if (value == null) {
            return;
        }
        try {
            verifyNode(mapper.readTree(value), field, 0);
        } catch (IOException exception) {
            throw new IllegalArgumentException("저장할 출력 항목 형식이 올바르지 않습니다.");
        }
    }

    private void verifyNode(JsonNode value, String field, int depth) {
        if (depth > MAX_METADATA_DEPTH) {
            throw new IllegalArgumentException("출력 항목의 중첩 범위를 초과했습니다.");
        }
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isTextual()) {
            verifyText(value.asText(), field);
        } else if (value.isContainerNode()) {
            for (JsonNode child : value) {
                verifyNode(child, field, depth + 1);
            }
        }
    }

    private void verifyValues(Object value, String field, int depth) {
        if (depth > MAX_METADATA_DEPTH) {
            throw new IllegalArgumentException("출력 항목의 중첩 범위를 초과했습니다.");
        }
        if (value instanceof String text) {
            verifyText(text, field);
        } else if (value instanceof java.util.Map<?, ?> map) {
            for (Object entry : map.values()) {
                verifyValues(entry, field, depth + 1);
            }
        } else if (value instanceof Iterable<?> values) {
            for (Object entry : values) {
                verifyValues(entry, field, depth + 1);
            }
        }
    }

    private void verifyText(String value, String field) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = normalize(value);
        List<Span> allowed = allowedWords(normalized);
        List<String> detected = new ArrayList<>();
        for (CompiledRule rule : rules) {
            var matcher = rule.pattern().matcher(normalized);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                if (allowed.stream().anyMatch(span -> start >= span.start() && end <= span.end())) {
                    continue;
                }
                if (!leftBoundary(normalized, start, rule.rule().initial())) {
                    continue;
                }
                boolean separated = !matcher.group().equals(rule.term());
                Pattern suffix = separated && !rule.rule().initial() ? SEPARATED_SUFFIX : SUFFIX;
                if (end < normalized.length() && Character.isLetterOrDigit(normalized.charAt(end))
                        && !suffix.matcher(normalized.substring(end)).matches()
                        && (rule.rule().initial() || separated || rule.term().equals("시발")
                                || !JOINED_QUERY.matcher(normalized.substring(end)).matches())) {
                    continue;
                }
                detected.add(rule.rule().id());
                break;
            }
        }
        if (!detected.isEmpty()) {
            throw new ChatOutputBlockedException(policy.version(), field, detected);
        }
    }

    private boolean leftBoundary(String text, int start, boolean initial) {
        if (start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1))) {
            return true;
        }
        if (initial) {
            return Character.UnicodeBlock.of(text.charAt(start - 1)) == Character.UnicodeBlock.HANGUL_SYLLABLES;
        }
        int first = start - 1;
        while (first > 0 && Character.isLetterOrDigit(text.charAt(first - 1))) {
            first--;
        }
        return policy.joinedPrefixes().contains(text.substring(first, start));
    }

    private List<Span> allowedWords(String text) {
        List<Span> result = new ArrayList<>();
        for (String word : policy.allowWords()) {
            String normalized = normalize(word);
            for (int index = text.indexOf(normalized); index >= 0; index = text.indexOf(normalized, index + 1)) {
                result.add(new Span(index, index + normalized.length()));
            }
        }
        return result;
    }

    private String normalize(String value) {
        StringBuilder normalized = new StringBuilder();
        int chunkStart = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.UnicodeBlock.of(current) != Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) {
                continue;
            }
            normalized.append(Normalizer.normalize(value.substring(chunkStart, index), Normalizer.Form.NFKC));
            normalized.append(Normalizer.normalize(String.valueOf(current), Normalizer.Form.NFKC));
            chunkStart = index + 1;
        }
        normalized.append(Normalizer.normalize(value.substring(chunkStart), Normalizer.Form.NFKC));
        return normalized.toString().toLowerCase(Locale.ROOT);
    }

    private record Policy(String version, List<String> allowWords, List<String> joinedPrefixes, List<Rule> rules) {}

    private record Rule(String id, String term, boolean initial) {}

    private record CompiledRule(Rule rule, String term, Pattern pattern) {}

    private record Span(int start, int end) {}
}
