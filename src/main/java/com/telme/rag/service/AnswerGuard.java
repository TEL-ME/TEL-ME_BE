package com.telme.rag.service;

import com.telme.global.common.exception.GeneralException;
import com.telme.llm.exception.LlmErrorCode;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 프롬프트로 금지해도 모델이 지키지 않는 부분을 코드로 막는다.
// 평가 질문 30건으로 측정한 결과를 근거로 두 가지만 처리한다
@Slf4j
@Component
public class AnswerGuard {

    private static final Pattern AMOUNT = Pattern.compile("(\\d[\\d,]*)\\s*원");

    // "안내드릴 수 있는 정보가 없습니다" 뒤에 설명을 덧붙이는 경우가 10건 중 6건
    public String trimAfterNoEvidence(String answer) {
        if (!answer.contains(AnswerPromptTemplates.NO_EVIDENCE_ANSWER)) {
            return answer;
        }
        return AnswerPromptTemplates.NO_EVIDENCE_ANSWER;
    }

    // 근거의 금액을 더하거나 곱해 새 금액을 만드는 경우가 있어 근거에 없는 금액이면 실패시킨다
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
