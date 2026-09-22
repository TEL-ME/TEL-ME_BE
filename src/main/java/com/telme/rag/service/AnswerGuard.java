package com.telme.rag.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmErrorCode;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AnswerGuard {

    private static final Pattern AMOUNT = Pattern.compile("(\\d[\\d,]*)\\s*원");

    // 모델이 답변 불가 문구 뒤에 설명을 덧붙이는 경우가 있음
    public String trimAfterNoEvidence(String answer) {
        if (!answer.contains(AnswerPromptTemplates.NO_EVIDENCE_ANSWER)) {
            return answer;
        }
        return AnswerPromptTemplates.NO_EVIDENCE_ANSWER;
    }

    // 근거의 금액을 계산해 없던 금액을 만들어내는 경우가 있음
    public void verifyAmounts(String answer, String context) {
        Set<Long> invented = amountsIn(answer);
        invented.removeAll(amountsIn(context));
        if (!invented.isEmpty()) {
            log.warn("[AnswerGuard] 근거에 없는 금액 발견: {}", invented);
            throw new GeneralException(LlmErrorCode.INVALID_RESPONSE);
        }
    }

    private Set<Long> amountsIn(String text) {
        Matcher matcher = AMOUNT.matcher(text);
        return matcher.results()
                .map(result -> Long.parseLong(result.group(1).replace(",", "")))
                .collect(Collectors.toSet());
    }
}
