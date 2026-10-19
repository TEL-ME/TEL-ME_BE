package com.telme.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 같은 대상의 확정된 정정만 과거 기간 발언을 대체하고 질문·다른 기간 종류는 보존한다. */
final class ChatEndSummaryPeriodContext {
    record Quote(String text, String scope) {}

    private static final Pattern KIND = Pattern.compile("가입|약정|이용|사용");
    private static final Pattern CORRECTION = Pattern.compile("정정|아니라|잘못\\s*말|확인해\\s*보니");
    private static final Pattern UNCERTAIN = Pattern.compile("[?？]|인가요|일까요|맞나요|모르|아닐|아닌가");
    private static final Pattern POSITIVE_PERIOD = Pattern.compile(
            "[0-9]+\\s*(?:개월|년)(?:로|으로|입니다|이에요|예요|이\\s*맞|이었|이\\s*됐|이라고)");

    private ChatEndSummaryPeriodContext() {}

    static boolean isCorrection(String text) {
        return CORRECTION.matcher(text).find();
    }

    static boolean isConfirmedCorrection(String text) {
        return isCorrection(text) && !UNCERTAIN.matcher(text).find() && POSITIVE_PERIOD.matcher(text).find();
    }

    static boolean canStatePeriod(String text) {
        return !isCorrection(text) && !UNCERTAIN.matcher(text).find()
                && !text.matches("(?s).*(?:아니|않|미확인|확인\\s*필요).*");
    }

    static List<String> latest(List<String> quotes) {
        return latestScoped(quotes.stream().map(text -> new Quote(text, text)).toList());
    }

    static List<String> latestScoped(List<Quote> quotes) {
        List<Quote> result = new ArrayList<>();
        for (var quote : quotes) {
            if (isConfirmedCorrection(quote.text())) {
                String kind = kind(quote.text());
                result.removeIf(previous -> !kind.isBlank() && kind.equals(kind(previous.text()))
                        && ChatEndSummaryInquiryLinker.hasSamePeriodScope(previous, quote));
            }
            if (result.stream().noneMatch(previous -> previous.text().equals(quote.text()))) {
                result.add(quote);
            }
        }
        return result.stream().map(Quote::text).toList();
    }

    private static String kind(String text) {
        var kinds = KIND.matcher(text).results().map(match -> match.group()).distinct().toList();
        return kinds.size() == 1 ? kinds.getFirst() : "";
    }
}
