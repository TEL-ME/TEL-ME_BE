package com.telme.rag.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmErrorCode;
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

    private static final Pattern AMOUNT = Pattern.compile("(\\d[\\d,]*)\\s*원");

    // 모델이 답변 불가 문구 뒤에 설명을 덧붙이는 경우가 있음
    public String trimAfterNoEvidence(String answer) {
        int found = answer.indexOf(AnswerPromptTemplates.NO_EVIDENCE_ANSWER);
        if (found < 0) {
            return answer;
        }
        return answer.substring(0, found + AnswerPromptTemplates.NO_EVIDENCE_ANSWER.length()).strip();
    }

    // 근거의 금액을 계산해 없던 금액을 만들어내는 경우가 있음
    public void verifyAmounts(String answer, String context, String userQuery) {
        Set<BigInteger> invented = amountsIn(answer);
        invented.removeAll(amountsIn(context));
        // 고객이 질문에 쓴 금액을 되받는 것은 지어낸 값이 아님
        invented.removeAll(amountsIn(userQuery));
        if (!invented.isEmpty()) {
            log.warn("[AnswerGuard] 근거에 없는 금액 발견: {}", invented);
            throw new GeneralException(LlmErrorCode.INVALID_RESPONSE);
        }
    }

    private Set<BigInteger> amountsIn(String text) {
        if (text == null) {
            return new LinkedHashSet<>();
        }
        Set<BigInteger> amounts = new LinkedHashSet<>();
        Matcher matcher = AMOUNT.matcher(text);
        while (matcher.find()) {
            amounts.add(new BigInteger(matcher.group(1).replace(",", "")));
        }
        return amounts;
    }
}
