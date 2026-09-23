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

    // 억·만 단위와 원 단위를 함께 잡는다. "30만 원"과 "300,000원"이 같은 값이어야 한다
    private static final Pattern AMOUNT = Pattern.compile(
            "(?:(\\d[\\d,]*)\\s*억\\s*)?(?:(\\d[\\d,]*)\\s*만\\s*)?(\\d[\\d,]*)?\\s*원");

    private static final BigInteger[] UNITS = {
            BigInteger.valueOf(100_000_000L), BigInteger.valueOf(10_000L), BigInteger.ONE
    };

    // 모델이 답변 불가 문구 뒤에 설명을 덧붙이는 경우가 있음.
    // 조건별 안내 중간에 나온 문구는 뒤 내용이 사라지므로 건드리지 않는다
    public String trimAfterNoEvidence(String answer) {
        if (answer == null) {
            return "";
        }
        String trimmed = answer.strip();
        if (!trimmed.startsWith(AnswerPromptTemplates.NO_EVIDENCE_ANSWER)) {
            return answer;
        }
        return AnswerPromptTemplates.NO_EVIDENCE_ANSWER;
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
