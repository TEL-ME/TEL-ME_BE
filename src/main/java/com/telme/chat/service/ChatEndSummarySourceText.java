package com.telme.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 문장을 새로 쓰지 않고 제목·원문 구간의 경계만 정리한다. */
final class ChatEndSummarySourceText {
    private static final Pattern HEADING = Pattern.compile("^(?:[0-9]+[.)]|#{1,6})\\s+(.+)$");
    private static final Pattern VALUE = Pattern.compile("[0-9][0-9,]*\\s*(?:원|개월|일|년|GB|MB)");
    private static final Pattern PERIOD = Pattern.compile("(?<![0-9])[0-9]{1,4}\\s*(?:개월|년)");
    private static final Pattern CONTRACT = Pattern.compile("가입|약정|이용|사용");
    private static final Pattern DEPENDENT = Pattern.compile("^(?:이|그|해당)\\s*(?:경우|때|조건|대상)");
    private static final List<String> REQUEST_ENDINGS = List.of(
            "알려주세요", "말씀해주세요", "선택해주세요", "입력해주세요");

    private ChatEndSummarySourceText() {}

    static String summaryMessageType(String type, String basis, String content) {
        // 라우팅 확인 요청은 ANSWER로 저장되므로 이 고정 문구만 정확히 구분한다.
        return "ANSWER".equals(type) && basis == null && isTopicClarification(content)
                ? "CLARIFICATION" : type;
    }

    static boolean isTopicClarification(String content) {
        return ("어떤 내용에 대한 질문인지 확인이 필요합니다. "
                + "상품이나 상담 주제를 조금 더 구체적으로 알려주세요.").equals(content);
    }

    static List<String> paragraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        for (String source : text.split("\\R", -1)) {
            String line = source.strip();
            if (line.isBlank()) {
                flush(lines, paragraphs);
            } else if (isHeading(line)) {
                String heading = HEADING.matcher(line).replaceFirst("$1");
                // 예외 소제목 때문에 조건과 본문이 서로 다른 후보가 되지 않게 한다.
                if (!heading.matches("^(?:조건|예외|주의|유의|제한|단서).*")) {
                    flush(lines, paragraphs);
                }
            } else {
                lines.add(line);
            }
        }
        flush(lines, paragraphs);
        return List.copyOf(paragraphs);
    }

    static List<String> periodQuotes(String text) {
        return sentences(text).stream()
                .filter(sentence -> PERIOD.matcher(sentence).find() && CONTRACT.matcher(sentence).find())
                .distinct().toList();
    }

    static String clarificationQuestion(String text) {
        List<String> questions = paragraphs(text).stream().flatMap(paragraph -> sentences(paragraph).stream())
                .filter(ChatEndSummarySourceText::isQuestion).distinct().toList();
        // 명사형 확인 항목처럼 질문 표지가 없는 기존 문구는 임의로 버리지 않는다.
        return questions.isEmpty() ? text : String.join(" ", questions);
    }

    static boolean containsSentences(String longer, String shorter) {
        List<String> source = sentences(longer);
        List<String> quote = sentences(shorter);
        if (quote.isEmpty() || source.size() <= quote.size()) {
            return false;
        }
        for (int start = 0; start <= source.size() - quote.size(); start++) {
            if (source.subList(start, start + quote.size()).equals(quote)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHeading(String line) {
        var match = HEADING.matcher(line);
        if (!match.matches()) {
            return false;
        }
        String text = match.group(1);
        // 짧은 정책 문구와 숫자가 있는 안내는 제목처럼 보여도 유지한다.
        return !VALUE.matcher(text).find()
                && !text.matches(".*(?:[.!?]|니다|세요|해요|이에요|예요|있음|없음|무료|유료|불가|가능|필요|불필요|미처리)$");
    }

    private static boolean isQuestion(String text) {
        String compact = text.replaceAll("(?U)\\s+", "").replaceAll("[.!]+$", "");
        return text.endsWith("?") || REQUEST_ENDINGS.stream().anyMatch(compact::endsWith);
    }

    static List<String> sentences(String text) {
        return List.of(text.split("(?<=[.!?])\\s+|\\R+")).stream()
                .map(String::strip).filter(sentence -> !sentence.isBlank()).toList();
    }

    static String deduplicate(List<String> paragraphs) {
        var seen = new java.util.LinkedHashSet<String>();
        List<String> result = new ArrayList<>();
        for (String paragraph : paragraphs) {
            for (String sentence : sentences(paragraph)) {
                // 앞 조건을 가리키는 같은 문장은 서로 다른 조건의 안내일 수 있다.
                if (DEPENDENT.matcher(sentence).find() || seen.add(sentence)) {
                    result.add(sentence);
                }
            }
        }
        return String.join(" ", result);
    }

    private static void flush(List<String> lines, List<String> paragraphs) {
        if (!lines.isEmpty()) {
            paragraphs.add(String.join("\n", lines));
            lines.clear();
        }
    }
}
