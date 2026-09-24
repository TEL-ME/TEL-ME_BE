package com.telme.rag.service;

import com.telme.rag.exception.AnswerGuardException;
import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AnswerGuard {

    // 억·만 단위와 원 단위를 함께 잡는다. "30만 원"과 "300,000원"이 같은 값이어야 한다.
    // 공백은 단위 뒤에만 허용한다. "1 원금"처럼 원으로 시작하는 낱말을 금액으로 읽지 않기 위해서다
    private static final Pattern AMOUNT = Pattern.compile(
            "(?:(\\d[\\d,]*)\\s*억\\s*)?(?:(\\d[\\d,]*)\\s*만\\s*)?(\\d[\\d,]*)?원");

    // "3영업일"처럼 숫자와 단위 사이에 글자가 끼면 걸리지 않는다
    private static final Pattern MEASURE = Pattern.compile(
            "(\\d[\\d,]*(?:\\.\\d+)?)\\s*(배|%|일|개월|시간|분|GB|회|년)");

    private static final BigInteger[] UNITS = {
            BigInteger.valueOf(100_000_000L), BigInteger.valueOf(10_000L), BigInteger.ONE
    };

    // 답변 불가 문구 뒤에 붙는 내용은 근거 밖 설명이라 잘라낸다. 앞은 서두일 수도 있어 그대로 둔다
    public String trimAfterNoEvidence(String answer) {
        if (answer == null) {
            return "";
        }
        int found = answer.indexOf(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        if (found < 0) {
            return answer;
        }
        return answer.substring(0, found + AnswerPromptTemplates.NO_EVIDENCE_ANSWER.length()).strip();
    }

    // 프롬프트 3번 규칙이 있어도 지어내 문장째 걷어낸다
    private static final Set<String> CHANNELS = Set.of(
            "샵", "이벤트", "홈페이지", "페이지", "사이트", "앱", "메뉴",
            "고객센터", "콜센터", "매장", "대리점", "지점"
    );

    private static final Set<String> COMPARISONS = Set.of(
            "보다", "제일", "가장", "유리", "저렴", "비싸", "빠릅", "편리", "나은", "낫습", "우수"
    );

    // 나머지 문장은 근거대로인 경우가 많아 금액과 달리 답변 전체를 막지 않는다
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+");

    public String trimUngroundedComparisons(String answer, String context, String userQuery) {
        return trimSentences(answer, context, userQuery, COMPARISONS, "근거에 없는 비교 표현");
    }

    public String trimUngroundedChannels(String answer, String context, String userQuery) {
        return trimSentences(answer, context, userQuery, CHANNELS, "근거에 없는 안내 창구");
    }

    private String trimSentences(
            String answer, String context, String userQuery, Set<String> words, String reason) {
        if (answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        String allowed = (context == null ? "" : context) + " " + (userQuery == null ? "" : userQuery);
        StringBuilder kept = new StringBuilder();
        for (String sentence : SENTENCE.split(answer.strip())) {
            Set<String> invented = ungrounded(sentence, allowed, words);
            if (invented.isEmpty()) {
                kept.append(kept.isEmpty() ? "" : " ").append(sentence);
                continue;
            }
            log.warn("[AnswerGuard] {}로 문장 제거: {} | {}", reason, invented, sentence);
        }
        // 지어낸 문장만 있던 답변이라 근거 없음으로 돌린다
        return kept.isEmpty() ? AnswerPromptTemplates.NO_EVIDENCE_ANSWER : kept.toString();
    }

    private Set<String> ungrounded(String sentence, String allowed, Set<String> words) {
        Set<String> invented = new LinkedHashSet<>();
        for (String channel : words) {
            if (sentence.contains(channel) && !allowed.contains(channel)) {
                invented.add(channel);
            }
        }
        return invented;
    }

    // 근거의 금액을 계산해 없던 금액을 만들어내는 경우가 있음
    public void verifyAmounts(String answer, String context, String userQuery) {
        Set<BigInteger> invented = amountsIn(answer);
        invented.removeAll(amountsIn(context));
        // 고객이 질문에 쓴 금액을 되받는 것은 지어낸 값이 아님
        invented.removeAll(amountsIn(userQuery));
        if (!invented.isEmpty()) {
            log.warn("[AnswerGuard] 근거에 없는 금액 발견: {}", invented);
            throw new AnswerGuardException("근거에 없는 금액: " + invented);
        }
    }

    public void verifyMeasures(String answer, String context, String userQuery) {
        Set<String> invented = measuresIn(answer);
        invented.removeAll(measuresIn(context));
        invented.removeAll(measuresIn(userQuery));
        if (!invented.isEmpty()) {
            log.warn("[AnswerGuard] 근거에 없는 수치 발견: {}", invented);
            throw new AnswerGuardException("근거에 없는 수치: " + invented);
        }
    }

    private Set<String> measuresIn(String text) {
        Set<String> measures = new LinkedHashSet<>();
        if (text == null) {
            return measures;
        }
        Matcher matcher = MEASURE.matcher(text);
        while (matcher.find()) {
            measures.add(matcher.group(1).replace(",", "") + matcher.group(2));
        }
        return measures;
    }

    private Set<BigInteger> amountsIn(String text) {
        Set<BigInteger> amounts = new LinkedHashSet<>();
        if (text == null) {
            return amounts;
        }
        Matcher matcher = AMOUNT.matcher(text);
        while (matcher.find()) {
            BigInteger amount = toAmount(matcher);
            if (amount != null) {
                amounts.add(amount);
            }
        }
        return amounts;
    }

    // 숫자가 하나도 없이 "원"만 걸린 경우는 금액이 아님
    private BigInteger toAmount(Matcher matcher) {
        BigInteger total = BigInteger.ZERO;
        boolean found = false;
        for (int group = 1; group <= UNITS.length; group++) {
            String digits = matcher.group(group);
            if (digits == null) {
                continue;
            }
            found = true;
            total = total.add(new BigInteger(digits.replace(",", "")).multiply(UNITS[group - 1]));
        }
        return found ? total : null;
    }
}
