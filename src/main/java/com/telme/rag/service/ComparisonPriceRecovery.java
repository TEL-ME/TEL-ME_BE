package com.telme.rag.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 해석 가능한 가격 안내 전체에서만 원문을 복구한다. 알 수 없는 적용 조건은 추측하지 않는다.
final class ComparisonPriceRecovery {

    private static final int MAX_SENTENCE_LENGTH = 512;
    private static final int MAX_CONTEXT_LENGTH = 2048;
    private static final int MAX_QUERY_LENGTH = 256;
    private static final Pattern WHITESPACE = Pattern.compile("[\\s\\u00a0\\u202f]++");
    private static final String DAYS = "[1-9][0-9]{0,2}";
    private static final String MONEY = "(?:[1-9][0-9]{0,8}|[1-9][0-9]{0,2}(?:,[0-9]{3}){1,2})";
    private static final String PRODUCT = "[가-힣A-Za-z]{1,30}?";
    private static final String INTRO = "(?:(?<intro>일주일(?:\\(7일\\))?|" + DAYS
            + "일(?:\\(" + DAYS + "일\\))?)(?: 로밍(?:을|를)?)?"
            + "(?: 이용하신다면| 이용하면| 사용하시면|이면)[, ]*)?";
    private static final String OFFER = "(?<days>" + DAYS + ")일 (?<product>" + PRODUCT + ")";
    private static final String AMOUNT = "(?<amount>" + MONEY + ") ?원";
    private static final String CHEAP = "(?:가장|제일) (?:저렴합니다|저렴해요|쌉니다|싸요)[.!]?";
    private static final List<Pattern> CLAIMS = List.of(
            Pattern.compile(INTRO + OFFER + "(?:이|가|은|는)? " + AMOUNT + "(?:이|으로|에)? " + CHEAP),
            Pattern.compile("(?<intro>)(?:가장|제일) 저렴한 상품은 " + AMOUNT + "짜리 " + OFFER + "입니다[.!]?"),
            Pattern.compile("(?<intro>)" + OFFER + "(?:이|가|은|는) (?:가장|제일) 저렴하고 가격은 "
                    + AMOUNT + "입니다[.!]?"));
    private static final Pattern SOURCE = Pattern.compile(INTRO + OFFER + "(?:이|가|은|는)? "
            + AMOUNT + "(?:이|으로|에)? (?:가장|제일) 유리합니다[.!]?");
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?]) +");
    private static final String PERIOD = "(?<period>일주일|한 주|한주|" + DAYS + "일)(?:간)?";
    private static final String QUESTION = "(?:어떤 로밍 상품이 좋을까요|(?:로밍(?:은|을)? )?"
            + "(?:뭐가|어떤 상품이) (?:(?:가장|제일) )?(?:싸요|저렴해요))[?!.]?";
    private static final Pattern QUERY = Pattern.compile(
            "(?:(?:담주에|다음 주에|다음주에) )?(?:여행 기간은 )?" + PERIOD
                    + "(?:이에요[.]? | (?:출장가는데|여행하는데|여행가는데) | 동안 (?:로밍을 )?(?:쓸 건데|이용해요)[.]? | )" + QUESTION);

    private ComparisonPriceRecovery() {}

    static String recover(String sentence, String context, String userQuery) {
        if (!isBounded(sentence, MAX_SENTENCE_LENGTH) || !isBounded(context, MAX_CONTEXT_LENGTH)
                || !isBounded(userQuery, MAX_QUERY_LENGTH)) {
            return null;
        }
        Matcher answer = matchClaim(normalize(sentence));
        if (answer == null || !hasConsistentPeriod(answer)
                || !hasCompatibleQueryPeriod(normalize(userQuery), answer.group("days"))) {
            return null;
        }
        String source = normalize(context);
        if (source.startsWith("- ")) {
            source = source.substring(2);
        }
        String[] sentences = SENTENCE.split(source);
        Matcher evidence = SOURCE.matcher(sentences[0]);
        if (sentences.length > 2 || !evidence.matches() || !hasConsistentPeriod(evidence)
                || !sameOffer(answer, evidence) || !hasRecognizedRemainder(sentences, evidence.group("product"))) {
            return null;
        }
        // 출처의 공백과 문장부호는 그대로 보존하며, 조건을 떼어 낸 부분 인용은 허용하지 않는다.
        String original = context.strip();
        if (original.startsWith("- ")) {
            original = original.substring(2);
        }
        int end = findFirstSentenceEnd(original);
        return original.substring(0, end).strip();
    }

    private static boolean isBounded(String value, int maximum) {
        return value != null && value.length() <= maximum && !value.isBlank();
    }

    private static String normalize(String value) {
        return WHITESPACE.matcher(value.strip()).replaceAll(" ");
    }

    private static Matcher matchClaim(String sentence) {
        for (Pattern pattern : CLAIMS) {
            Matcher match = pattern.matcher(sentence);
            if (match.matches()) {
                return match;
            }
        }
        return null;
    }

    private static boolean hasRecognizedRemainder(String[] sentences, String product) {
        if (sentences.length == 1) {
            return true;
        }
        // 허용 문법 밖의 문장은 조건·정정·추가 상품일 수 있으므로 복구하지 않는다.
        String relation = "일 단위 요금제는 " + MONEY + " ?원, 데이터 무제한은 하루 " + MONEY
                + " ?원이라 같은 기간을 쓰시면 " + Pattern.quote(product) + "보다 비쌉니다[.!]?";
        return sentences[1].matches(relation);
    }

    private static boolean hasCompatibleQueryPeriod(String query, String days) {
        Matcher match = QUERY.matcher(query);
        if (!match.matches()) {
            return false;
        }
        String period = match.group("period");
        return period.equals(days + "일")
                || days.equals("7") && List.of("일주일", "한 주", "한주").contains(period);
    }

    private static boolean sameOffer(Matcher answer, Matcher evidence) {
        return answer.group("days").equals(evidence.group("days"))
                && answer.group("product").equals(evidence.group("product"))
                && answer.group("amount").replace(",", "").equals(evidence.group("amount").replace(",", ""));
    }

    private static boolean hasConsistentPeriod(Matcher offer) {
        String intro = offer.group("intro");
        if (intro == null || intro.isEmpty()) {
            return true;
        }
        String days = offer.group("days");
        return intro.startsWith("일주일") ? days.equals("7")
                : intro.equals(days + "일") || intro.equals(days + "일(" + days + "일)");
    }

    private static int findFirstSentenceEnd(String source) {
        for (int index = 0; index < source.length() - 1; index++) {
            if (".!?".indexOf(source.charAt(index)) >= 0
                    && (Character.isWhitespace(source.charAt(index + 1))
                    || Character.isSpaceChar(source.charAt(index + 1)))) {
                return index + 1;
            }
        }
        return source.length();
    }
}
