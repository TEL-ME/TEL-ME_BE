package com.telme.rag.service;

import com.telme.rag.dto.req.AnswerRequest;
import java.util.Map;
import java.util.stream.Collectors;

public final class AnswerPromptTemplates {

    private AnswerPromptTemplates() {}

    // 프롬프트 2번 규칙의 문구와 동일하게 유지
    public static final String NO_EVIDENCE_ANSWER = "안내드릴 수 있는 정보가 없습니다.";

    public static final String ANSWER_SYSTEM_PROMPT = """
        당신은 LG U+ 통신 고객센터 AI 상담사입니다.
        아래에 주어진 FAQ 근거만 사용해 고객 질문에 답변하십시오.

        [답변 규칙]
        1. 근거에 없는 내용은 절대 만들어내지 마십시오. 추측하거나 일반 상식으로 채우지 마십시오.
        2. 근거로 답할 수 없는 질문이면 "안내드릴 수 있는 정보가 없습니다"라고만 답하십시오.
        3. 고객이 제시한 조건이 있으면 그 조건에 해당하는 내용만 골라 답변하십시오.
        4. 근거에 조건별로 다른 내용이 있는데 고객 조건을 모르면, 조건을 나누어 모두 안내하십시오.
        5. 존댓말로 간결하게 답하십시오. 3~5문장을 넘기지 마십시오.
        6. 근거 번호([1], [2])나 "FAQ에 따르면" 같은 표현은 답변에 쓰지 마십시오.
        """;

    public static String buildUserPrompt(AnswerRequest request, String context) {
        return """
            [FAQ 근거]
            %s

            [고객 조건]
            %s

            [고객 질문]
            %s""".formatted(context, formatConditions(request.conditions()), request.userQuery());
    }

    private static String formatConditions(Map<String, String> conditions) {
        String formatted = conditions.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> "- %s: %s".formatted(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("\n"));
        return formatted.isEmpty() ? "없음" : formatted;
    }
}
