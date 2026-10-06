package com.telme.consult.service;

import com.telme.faq.dto.res.FaqSearchResponse;
import java.util.List;
import java.util.stream.IntStream;

public final class ConditionPromptTemplates {

    private ConditionPromptTemplates() {}

    // 아래 프롬프트를 고치면 함께 올린다. 개선 전후 비교에 쓰인다
    public static final String PROMPT_VERSION = "condition-extract-v1.0";

    public static final String CONDITION_SYSTEM_PROMPT = """
        당신은 LG U+ 통신 고객센터 상담의 질문 분석기입니다.
        FAQ 답변(A)만 보고, 고객에게 답을 확정해 주기 위해 더 알아야 하는 것이 있는지 판단하십시오.

        [규칙]
        1. FAQ 답변(A)이 조건에 따라 내용이 갈리는데 고객이 그 조건을 말하지 않았을 때만 조건을 적으십시오.
        2. 답변(A)에 근거가 없는 조건은 적지 마십시오. 일반 상식으로 떠올린 조건도 적지 마십시오.
        3. 조건의 값을 추측하지 마십시오. 고객이 어떤 상황인지 단정하지 마십시오.
        4. evidence에는 그 조건이 필요하다고 말하는 답변(A)의 문장을 그대로 옮기십시오. 고쳐 쓰지 마십시오.
        5. 답변(A)에 고를 수 있는 값이 적혀 있을 때만 options에 그 값을 적으십시오.
           적혀 있지 않으면 options를 빈 배열로 두십시오. 값을 지어내지 마십시오.
        6. 중요한 것부터 최대 2개까지만 적으십시오. 더 알 것이 없으면 conditions를 빈 배열로 두십시오.
        7. key는 영문 소문자와 밑줄만 써서 짧게 지으십시오.
        8. question은 고객에게 그대로 보여줄 한 문장 질문으로, 존댓말로 쓰십시오.

        [출력 형식]
        아래 JSON만 출력하십시오. 설명을 덧붙이지 마십시오.
        {"conditions":[{"key":"","question":"","options":[],"evidence":""}]}
        """;

    public static String buildUserPrompt(String userQuery, List<FaqSearchResponse> sources) {
        return """
            [FAQ 근거]
            %s

            [고객 질문]
            %s""".formatted(formatSources(sources), userQuery);
    }

    private static String formatSources(List<FaqSearchResponse> sources) {
        if (sources == null || sources.isEmpty()) {
            return "없음";
        }
        return IntStream.range(0, sources.size())
                .mapToObj(index -> "[%d] Q. %s\nA. %s".formatted(
                        index + 1, sources.get(index).question(), sources.get(index).answer()))
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("없음");
    }
}
