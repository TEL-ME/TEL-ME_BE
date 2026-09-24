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

    // 근거에 없는 안내 창구를 지어내는 경우가 있음. 프롬프트 3번 규칙이 있어도 막히지 않아 문장째 걷어낸다.
    // FAQ 1,150건에 "샵", "이벤트"는 한 번도 없는데 "샵 이벤트 페이지"가 답변에 나온다
    private static final Set<String> CHANNELS = Set.of(
            "샵", "이벤트", "홈페이지", "페이지", "사이트", "앱", "메뉴",
            "고객센터", "콜센터", "매장", "대리점", "지점"
    );

    // 문장 끝에서 자른다. 금액과 달리 답변 전체를 막지 않는 것은 나머지 문장은 근거대로인 경우가 많아서다
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+");

    public String trimUngroundedChannels(String answer, String context, String userQuery) {
        if (answer == null || answer.isBlank()) {
            return answer == null ? "" : answer;
        }
        String allowed = (context == null ? "" : context) + " " + (userQuery == null ? "" : userQuery);
        StringBuilder kept = new StringBuilder();
        for (String sentence : SENTENCE.split(answer.strip())) {
            Set<String> invented = ungroundedChannels(sentence, allowed);
            if (invented.isEmpty()) {
                kept.append(kept.isEmpty() ? "" : " ").append(sentence);
                continue;
            }
            log.warn("[AnswerGuard] 근거에 없는 안내 창구로 문장 제거: {} | {}", invented, sentence);
        }
        // 전부 걷히면 남길 내용이 없다. 지어낸 안내만 있던 답변이라 근거 없음으로 돌린다
        return kept.isEmpty() ? AnswerPromptTemplates.NO_EVIDENCE_ANSWER : kept.toString();
    }

    private Set<String> ungroundedChannels(String sentence, String allowed) {
        Set<String> invented = new LinkedHashSet<>();
        for (String channel : CHANNELS) {
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
