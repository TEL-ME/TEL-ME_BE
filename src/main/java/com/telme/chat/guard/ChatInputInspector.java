package com.telme.chat.guard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.guard.InputInspection.Detection;
import com.telme.chat.guard.InputInspection.Reason;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 규칙 기반 입력 검사. 민감정보는 먼저 치환하며 원문 매칭 값은 결과에 남기지 않는다. */
@Component
public final class ChatInputInspector {
    private static final Pattern RESIDENT = Pattern.compile("(?<!\\d)(\\d{6})[ -]?(\\d{7})(?!\\d)");
    private static final Pattern CARD = Pattern.compile("(?<!\\d)\\d(?:[ -]?\\d){12,18}(?!\\d)");
    private static final Pattern QUOTED = Pattern.compile("['\"‘“]([^'\"’”]{1,60})['\"’”]");
    private static final Pattern MEANING =
            Pattern.compile(
                    "^(?:이라는|라는|이란|란|이|의)?(?:말|단어|표현|욕설)?(?:은|는|의|이)?"
                            + "(?:무슨)?(?:뜻|의미)(?:(?:이|가|은|는)?(?:뭐|무엇|뭔|알려|설명).*"
                            + "|이에요|인가요|입니까|예요|이야|이죠)$");
    private static final Pattern REPORTED =
            Pattern.compile(
                    "^(?:(?:이라는|라는)?(?:욕|말|표현)(?:을|를)?(?:들었|받았|당했)"
                            + "|(?:이라고|라고)(?:들었|전해들었)).*$");
    private static final Pattern REPORTED_SPEAKER =
            Pattern.compile(
                    "(?:상담원|직원|친구|상대방)(?:이|가)(?:(?:저|나)(?:를|한테|에게))?$");
    private static final Pattern REPORTED_SPEECH =
            Pattern.compile("^(?:이라고|라고)(?:했|말했|불렀).*$");
    private static final String DECORATION_SUFFIX =
            "["
                    + "ㅋㅎㅠㅜ".codePoints()
                            .mapToObj(value -> Normalizer.normalize(
                                    new String(Character.toChars(value)), Normalizer.Form.NFKC))
                            .collect(java.util.stream.Collectors.joining())
                    + "]{1,12}";
    private static final Pattern DECORATION_BOUNDARY =
            Pattern.compile("^" + DECORATION_SUFFIX + "(?:$|[^\\p{L}\\p{N}]).*", Pattern.DOTALL);
    private static final Pattern INITIAL_SUFFIX =
            Pattern.compile(
                    "^(?:아|야|이야|이냐|같아|같네|같은|놈|년|새끼|들|임|이네)"
                            + "(?:" + DECORATION_SUFFIX + ")?(?:$|[^\\p{L}\\p{N}]).*",
                    Pattern.DOTALL);
    private static final Pattern PROFANITY_SUFFIX =
            Pattern.compile(
                    "^(?:아|야|이야|이냐|이네|이다|이라고|이고|이요|이에요|입니다"
                            + "|같아|같네|같은|같다|같이|놈|년|새끼|들|임"
                            + "|하네|하냐|하지마|하지|하고|하는|하면|하다|해요|해){1,3}"
                            + "(?:" + DECORATION_SUFFIX + ")?(?:$|[^\\p{L}\\p{N}]).*",
                    Pattern.DOTALL);
    private static final Pattern SEPARATED_PROFANITY_SUFFIX =
            Pattern.compile(
                    "^(?:아|야|놈|년|새끼|들|같아|같네|같은){1,3}"
                            + "(?:" + DECORATION_SUFFIX + ")?(?:$|[^\\p{L}\\p{N}]).*",
                    Pattern.DOTALL);

    private static final Pattern INTRODUCTION_TOKEN =
            Pattern.compile("주민등록번호|주민번호|카드번호|카드|번호|입니다|이에요|예요|그리고|제|내|는|이|가|랑|와|과");

    private final Policy policy;
    private final List<CompiledRule> rules;

    public ChatInputInspector(ObjectMapper mapper) {
        try (var input = new ClassPathResource("chat/input-guard-rules.json").getInputStream()) {
            policy = mapper.readValue(input, Policy.class);
        } catch (IOException exception) {
            throw new IllegalStateException("채팅 입력 검사 규칙을 읽을 수 없습니다.", exception);
        }
        if (policy.version() == null
                || policy.version().isBlank()
                || policy.rules() == null
                || policy.rules().isEmpty()
                || policy.allowWords() == null
                || policy.allowWords().stream().anyMatch(word -> word == null || word.isBlank())) {
            throw new IllegalStateException("채팅 입력 검사 규칙이 비어 있습니다.");
        }
        Set<String> ids = new HashSet<>();
        rules =
                policy.rules().stream()
                        .map(
                                rule -> {
                                    if (rule.id() == null
                                            || rule.id().isBlank()
                                            || !ids.add(rule.id())
                                            || rule.term() == null
                                            || rule.term().isBlank()
                                            || rule.reason() == null
                                            || rule.reason() == Reason.SENSITIVE_INFORMATION) {
                                        throw new IllegalStateException(
                                                "채팅 욕설 규칙의 식별자·표현·사유를 확인해 주세요.");
                                    }
                                    String term = normalize(rule.term());
                                    String expression =
                                            term.codePoints()
                                                    .mapToObj(
                                                            value ->
                                                                    Pattern.quote(
                                                                            new String(
                                                                                    Character
                                                                                            .toChars(
                                                                                                    value))))
                                                    .collect(
                                                            java.util.stream.Collectors.joining(
                                                                    "[\\s._*\\-\\d\\u200B-\\u200D\\uFEFF]{0,6}"));
                                    return new CompiledRule(rule, Pattern.compile(expression));
                                })
                        .toList();
    }

    public String policyVersion() {
        return policy.version();
    }

    public InputInspection inspect(String input) {
        if (input == null) {
            throw new IllegalArgumentException("검사할 입력이 필요합니다.");
        }
        List<Detection> detections = new ArrayList<>();
        String safe = mask(input, detections);
        String normalized = normalize(safe);
        List<Span> exemptions = exemptions(normalized);
        for (CompiledRule compiled : rules) {
            Matcher matcher = compiled.pattern().matcher(normalized);
            while (matcher.find()) {
                if (covered(exemptions, matcher.start(), matcher.end())) {
                    continue;
                }
                if (compiled.rule().reason() == Reason.INITIAL_PROFANITY
                        && !initialBoundary(normalized, matcher.start(), matcher.end())) {
                    continue;
                }
                if (compiled.rule().reason() == Reason.PROFANITY
                        && !profanityBoundary(
                                normalized,
                                matcher.start(),
                                matcher.end(),
                                !matcher.group().equals(normalize(compiled.rule().term())))) {
                    continue;
                }
                detections.add(new Detection(compiled.rule().reason(), compiled.rule().id()));
                break;
            }
        }
        String remaining =
                safe.replace("[주민등록번호]", "")
                        .replace("[카드번호]", "")
                        .replaceAll("[\\s\\p{Punct}]", "");
        boolean masked =
                detections.stream()
                        .anyMatch(value -> value.reason() == Reason.SENSITIVE_INFORMATION);
        boolean hasQuestion = masked ? hasRemainingQuestion(remaining) : !safe.isBlank();
        return new InputInspection(safe, detections.stream().distinct().toList(), hasQuestion);
    }

    private boolean hasRemainingQuestion(String remaining) {
        Matcher labels = INTRODUCTION_TOKEN.matcher(remaining);
        int cursor = 0;
        while (cursor < remaining.length()) {
            if (!labels.region(cursor, remaining.length()).lookingAt()) {
                return true;
            }
            // 모든 토큰은 비어 있지 않으므로 입력 길이 이내에서 종료한다.
            cursor = labels.end();
        }
        return false;
    }

    private String mask(String input, List<Detection> detections) {
        List<Span> spans = new ArrayList<>();
        // 숫자와 구분자만 길이가 같은 문자로 정규화해 원문에서 치환할 위치를 보존한다.
        String numeric = numericForm(input);
        Matcher resident = RESIDENT.matcher(numeric);
        while (resident.find()) {
            if (residentDate(resident.group(1), resident.group(2).charAt(0))) {
                spans.add(new Span(resident.start(), resident.end(), "[주민등록번호]"));
                detections.add(new Detection(Reason.SENSITIVE_INFORMATION, "PII_RESIDENT_NUMBER"));
            }
        }
        Matcher card = CARD.matcher(numeric);
        while (card.find()) {
            // 뒤의 별도 숫자까지 붙은 후보가 검증에 실패해도 원래 번호의 그룹 경계를 확인한다.
            for (int end = card.end(); end > card.start(); end--) {
                if (end < card.end() && numeric.charAt(end) != ' ' && numeric.charAt(end) != '-') {
                    continue;
                }
                String digits = numeric.substring(card.start(), end).replaceAll("[ -]", "");
                if (!covered(spans, card.start(), end) && validCard(digits)) {
                    spans.add(new Span(card.start(), end, "[카드번호]"));
                    detections.add(new Detection(Reason.SENSITIVE_INFORMATION, "PII_PAYMENT_CARD"));
                    break;
                }
            }
        }
        spans.sort(Comparator.comparingInt(Span::start));
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        for (Span span : spans) {
            if (span.start() < cursor) {
                continue;
            }
            result.append(input, cursor, span.start()).append(span.replacement());
            cursor = span.end();
        }
        return result.append(input, cursor, input.length()).toString();
    }

    private String numericForm(String input) {
        StringBuilder normalized = new StringBuilder(input.length());
        for (int index = 0; index < input.length(); index++) {
            char value = input.charAt(index);
            int digit = Character.digit(value, 10);
            normalized.append(
                    digit >= 0
                            ? (char) ('0' + digit)
                            : value == '－' || value == '–' || value == '‑'
                                    ? '-'
                                    : value == '　' ? ' ' : value);
        }
        return normalized.toString();
    }

    private boolean residentDate(String date, char category) {
        if (category < '1' || category > '8') {
            return false;
        }
        int century =
                category == '3' || category == '4' || category == '7' || category == '8'
                        ? 2000
                        : 1900;
        try {
            LocalDate.of(
                    century + Integer.parseInt(date.substring(0, 2)),
                    Integer.parseInt(date.substring(2, 4)),
                    Integer.parseInt(date.substring(4, 6)));
            return true;
        } catch (DateTimeException exception) {
            return false;
        }
    }

    private boolean validCard(String digits) {
        if (digits.length() < 13 || digits.length() > 19 || !supportedCardPrefix(digits)) {
            return false;
        }
        int sum = 0;
        boolean doubled = false;
        for (int index = digits.length() - 1; index >= 0; index--) {
            int value = digits.charAt(index) - '0';
            if (doubled) {
                value *= 2;
                if (value > 9) {
                    value -= 9;
                }
            }
            sum += value;
            doubled = !doubled;
        }
        return sum % 10 == 0;
    }

    private boolean supportedCardPrefix(String digits) {
        if ("34569".indexOf(digits.charAt(0)) >= 0) {
            return true;
        }
        if (digits.length() != 16 || digits.charAt(0) != '2') {
            return false;
        }
        int bin = Integer.parseInt(digits.substring(0, 6));
        return bin >= 222100 && bin <= 272099;
    }

    private List<Span> exemptions(String text) {
        List<Span> spans = new ArrayList<>();
        for (String word : policy.allowWords()) {
            String normalized = normalize(word);
            for (int offset = text.indexOf(normalized);
                    offset >= 0;
                    offset = text.indexOf(normalized, offset + 1)) {
                spans.add(new Span(offset, offset + normalized.length(), ""));
            }
        }
        Matcher quotes = QUOTED.matcher(text);
        while (quotes.find()) {
            String tail = compact(text.substring(quotes.end()));
            boolean reportedByOther =
                    REPORTED_SPEECH.matcher(tail).matches()
                            && REPORTED_SPEAKER
                                    .matcher(compact(text.substring(0, quotes.start())))
                                    .find();
            if (MEANING.matcher(tail).matches()
                    || REPORTED.matcher(tail).matches()
                    || reportedByOther) {
                spans.add(new Span(quotes.start(1), quotes.end(1), ""));
            }
        }
        // 인용부호 없는 단어 설명은 시작 부분의 해당 표현만 예외 처리한다.
        for (CompiledRule rule : rules) {
            Matcher matcher = rule.pattern().matcher(text);
            if (matcher.find()
                    && matcher.start() == 0
                    && MEANING.matcher(compact(text.substring(matcher.end()))).matches()) {
                spans.add(new Span(matcher.start(), matcher.end(), ""));
            }
        }
        return spans;
    }

    private boolean initialBoundary(String text, int start, int end) {
        if (start > 0
                && Character.isLetter(text.charAt(start - 1))
                && Character.UnicodeBlock.of(text.charAt(start - 1))
                        != Character.UnicodeBlock.HANGUL_SYLLABLES) {
            return false;
        }
        return end == text.length()
                || !Character.isLetter(text.charAt(end))
                || DECORATION_BOUNDARY.matcher(text.substring(end)).matches()
                || INITIAL_SUFFIX.matcher(text.substring(end)).matches();
    }

    private boolean profanityBoundary(String text, int start, int end, boolean separated) {
        // 다른 단어의 끝·시작 글자를 구분자 너머로 연결해 욕설을 만들지 않는다.
        if (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) {
            return false;
        }
        // 분리 표기의 마지막 글자가 정상 단어의 일부일 수 있어 어미 범위를 더 좁힌다.
        Pattern suffix = separated ? SEPARATED_PROFANITY_SUFFIX : PROFANITY_SUFFIX;
        return end == text.length()
                || !Character.isLetterOrDigit(text.charAt(end))
                || DECORATION_BOUNDARY.matcher(text.substring(end)).matches()
                || suffix.matcher(text.substring(end)).matches();
    }

    private boolean covered(List<Span> spans, int start, int end) {
        return spans.stream().anyMatch(span -> start >= span.start() && end <= span.end());
    }

    private String normalize(String value) {
        StringBuilder normalized = new StringBuilder();
        int chunkStart = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.UnicodeBlock.of(current)
                    != Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) {
                continue;
            }
            // 초성과 뒤의 울음 자모가 합쳐져 다른 음절이 되지 않도록 자모만 따로 정규화한다.
            normalized.append(Normalizer.normalize(
                    value.substring(chunkStart, index), Normalizer.Form.NFKC));
            normalized.append(Normalizer.normalize(String.valueOf(current), Normalizer.Form.NFKC));
            chunkStart = index + 1;
        }
        normalized.append(Normalizer.normalize(value.substring(chunkStart), Normalizer.Form.NFKC));
        return normalized.toString().toLowerCase(java.util.Locale.ROOT);
    }

    private String compact(String value) {
        return value.replaceAll("[\\s\\p{Punct}]", "");
    }

    private record Policy(String version, List<Rule> rules, List<String> allowWords) {}

    private record Rule(String id, String term, Reason reason) {}

    private record CompiledRule(Rule rule, Pattern pattern) {}

    private record Span(int start, int end, String replacement) {}
}
