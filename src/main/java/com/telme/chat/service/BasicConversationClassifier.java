package com.telme.chat.service;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 전체 발화가 기본 대화일 때만 소비한다. 상담 내용을 부분 문자열 인사로 가로채지 않는다. */
public final class BasicConversationClassifier {
    public enum Type { GREETING, THANKS, FAREWELL, CALL, IDENTITY, HELP, WELLBEING, NONE }

    public record Result(Type type, boolean hasGreeting) {
        public boolean isPure() {
            return type != Type.NONE;
        }
    }

    private static final List<String> NAMES = List.of(
            "무러바라", "무러봐라", "물어바라", "물어봐라", "무러바", "바라", "mureobara");
    private static final List<String> ADDRESSES = NAMES.stream()
            .flatMap(name -> List.of(name + "야", name + "님", name + "씨", name).stream())
            .sorted(Comparator.comparingInt(String::length).reversed()).toList();
    private static final Map<Type, Set<String>> EXPRESSIONS = Map.of(
            Type.GREETING, Set.of("안녕", "안녕하세요", "안녕하십니까", "반가워", "반가워요", "반갑습니다",
                    "하이", "헬로", "hello", "hi", "좋은아침", "좋은아침이에요", "오랜만이야", "오랜만이에요"),
            Type.THANKS, Set.of("감사합니다", "감사해요", "감사", "고마워", "고마워요", "고맙습니다", "고맙네요",
                    "덕분이야", "덕분이에요", "정말고마워", "정말감사합니다", "너무고마워", "고마웠어", "thanks"),
            Type.FAREWELL, Set.of("안녕히계세요", "안녕히가세요", "잘가요", "잘가", "다음에또올게요",
                    "다음에또올게", "수고하세요", "수고하셨습니다", "다음에봐", "다음에봐요", "또보자", "바이", "bye"),
            Type.IDENTITY, Set.of("누구야", "너누구야", "누구세요", "이름이뭐야", "너이름이뭐야", "이름이뭐예요",
                    "이름이뭐에요", "너이름은", "이름은", "자기소개해줘", "자기소개해주세요", "너뭐하는친구야",
                    "넌누구야", "무슨챗봇이야", "어떤챗봇이야", "너는누구니"),
            Type.HELP, Set.of("도움말", "도와주세요", "도와줘", "뭐물어볼수있어요", "뭐물어볼수있어",
                    "무엇을물어볼수있나요", "어떤질문을할수있나요", "뭘할수있어요", "뭘할수있어",
                    "어떤상담을할수있나요", "뭐할수있어", "너뭐할수있어", "어떤걸도와줄수있어"),
            Type.WELLBEING, Set.of("잘지냈어", "잘지냈어요", "잘지내", "잘지내요", "잘지내고있어",
                    "뭐하고있어", "뭐하고있어요", "뭐해", "뭐하세요", "오늘어때", "오늘어때요",
                    "기분이어때", "기분이어때요", "잘있었어", "잘있었어요"));
    private static final List<String> PHRASES = EXPRESSIONS.values().stream().flatMap(Set::stream)
            .sorted(Comparator.comparingInt(String::length).reversed()).toList();
    private static final String ADDRESS_PATTERN = ADDRESSES.stream()
            .map(Pattern::quote).collect(Collectors.joining("|"));
    private static final String GREETING_PATTERN = EXPRESSIONS.get(Type.GREETING).stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .map(Pattern::quote).collect(Collectors.joining("|"));
    private static final Pattern LEADING_GREETING = Pattern.compile(
            "^(?:(?:" + ADDRESS_PATTERN + ")[\\s,，!！:：]*)(?:" + GREETING_PATTERN + ")"
                    + "(?=$|[\\s\\p{P}]|(?:" + ADDRESS_PATTERN + "))"
                    + "|^(?:" + GREETING_PATTERN + ")(?=$|[\\s\\p{P}]|(?:" + ADDRESS_PATTERN + "))");

    private BasicConversationClassifier() {}

    static List<String> consultationPhrases() {
        return java.util.stream.Stream.concat(ADDRESSES.stream(),
                        java.util.stream.Stream.of(Type.GREETING, Type.THANKS)
                                .flatMap(type -> EXPRESSIONS.get(type).stream()))
                // '감사 인사'처럼 명사로 쓰이는 표현은 상담 문장에서 제거하지 않는다.
                .filter(phrase -> !phrase.equals("감사"))
                .sorted(Comparator.comparingInt(String::length).reversed()).toList();
    }

    public static Result classify(String content) {
        if (content == null || content.isBlank()) return new Result(Type.NONE, false);
        String normalized = normalize(content);
        String remaining = compact(normalized);
        boolean addressed = false;
        boolean greeting = false;
        Type selected = Type.NONE;
        // 호칭과 기본 표현만으로 전체 문장이 소진되어야 순수 기본 대화다.
        while (!remaining.isEmpty()) {
            String address = findPrefix(remaining, ADDRESSES);
            if (address != null) {
                addressed = true;
                remaining = remaining.substring(address.length());
                continue;
            }
            String phrase = findPrefix(remaining, PHRASES);
            if (phrase == null) return new Result(Type.NONE, hasLeadingGreeting(content));
            Type type = EXPRESSIONS.entrySet().stream().filter(entry -> entry.getValue().contains(phrase))
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            greeting |= type == Type.GREETING;
            if (selected == Type.NONE || type != Type.GREETING) selected = type;
            remaining = remaining.substring(phrase.length());
        }
        return new Result(selected == Type.NONE && addressed ? Type.CALL : selected, greeting);
    }

    public static boolean hasLeadingGreeting(String content) {
        return content != null && LEADING_GREETING.matcher(normalize(content)).find();
    }

    private static String findPrefix(String content, List<String> values) {
        return values.stream().filter(content::startsWith).findFirst().orElse(null);
    }

    private static String normalize(String content) {
        return Normalizer.normalize(ConversationInput.normalize(content), Normalizer.Form.NFKC).strip().toLowerCase(java.util.Locale.ROOT);
    }

    private static String compact(String content) {
        return content.replaceAll("[\\p{javaWhitespace}\\p{Zs},，.!！?？~～…:：]+", "");
    }
}
