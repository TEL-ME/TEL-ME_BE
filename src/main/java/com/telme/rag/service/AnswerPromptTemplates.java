package com.telme.rag.service;

import com.telme.rag.dto.req.AnswerRequest;
import java.util.Map;
import java.util.stream.Collectors;

public final class AnswerPromptTemplates {

    private AnswerPromptTemplates() {}

    // 프롬프트 2번 규칙의 문구와 동일하게 유지
    public static final String NO_EVIDENCE_ANSWER = "안내드릴 수 있는 정보가 없습니다.";

    // 아래 프롬프트를 고치면 함께 올린다. 개선 전후 비교에 쓰인다
    public static final String PROMPT_VERSION = "rag-answer-v2";

    public static final String ANSWER_SYSTEM_PROMPT = """
        당신은 LG U+ 통신 고객센터 AI 상담사입니다.
        아래에 주어진 FAQ 근거만 사용해 고객 질문에 답변하십시오.

        [답변 규칙]
        1. 근거에 없는 내용은 절대 만들어내지 마십시오. 추측하거나 일반 상식으로 채우지 마십시오.
        2. 근거로 답할 수 없는 질문이면 "안내드릴 수 있는 정보가 없습니다" 한 문장만 출력하고 멈추십시오.
           사과, 이유 설명, 다른 곳 안내를 덧붙이지 마십시오.
        3. 근거에 없는 웹사이트, 페이지, 고객센터, 전화번호를 답변에 쓰지 마십시오.
        4. 근거에 있는 숫자만 쓰고, 근거의 숫자를 더하거나 곱해서 새 숫자를 만들지 마십시오.
        5. 고객이 제시한 조건이 있으면 그 조건에 해당하는 내용만 골라 답변하십시오.
        6. 근거에 조건별로 다른 내용이 있는데 고객 조건을 모르면, 조건을 나누어 모두 안내하십시오.
        7. 존댓말로 간결하게 답하십시오. 3~5문장을 넘기지 마십시오.
        8. 근거 번호([1], [2])나 "FAQ에 따르면", "제공된 근거에" 같은 표현을 쓰지 마십시오.
        9. 조건에 영문 코드가 있어도 답변에는 쓰지 말고 자연스러운 우리말로 바꿔 쓰십시오.
        10. "더 궁금한 점 있으시면", "문의해 주세요" 같은 맺음말을 붙이지 마십시오.
        """;

    // 조건 키를 읽기 쉬운 말로 변환
    private static final Map<String, String> CONDITION_LABELS = Map.of(
            "location", "지역",
            "serviceType", "업무 유형"
    );

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
                .map(entry -> "- %s: %s".formatted(
                        CONDITION_LABELS.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue()))
                .collect(Collectors.joining("\n"));
        return formatted.isEmpty() ? "없음" : formatted;
    }
}
