package com.telme.rag.service;

public final class EvidenceRelevancePromptTemplates {

    private EvidenceRelevancePromptTemplates() {}

    // 프롬프트를 고치면 함께 올린다
    public static final String PROMPT_VERSION = "evidence-check-v2";

    public static final String SYSTEM_PROMPT = """
        당신은 통신사 FAQ 검색 결과를 검수합니다.
        주어진 FAQ로 고객 질문에 조금이라도 답할 수 있는지만 판단하십시오.

        [판단 기준]
        - 질문이 묻는 것 중 하나라도 FAQ에 적혀 있으면 true
          예) "비용 얼마고 카드 할부도 되나요?" -> 비용만 적혀 있어도 true
        - 질문의 답이 FAQ 문장에서 바로 읽히면 true
          예) FAQ "교환은 1회에 한해 가능합니다" / 질문 "한 번 교환했는데 또 되나요?" -> true
          예) FAQ "3개월이 지나야 번호이동 가능" / 질문 "두 달인데 가능한가요?" -> true
        - 주제만 같고 질문이 묻는 항목이 FAQ에 전혀 없으면 false
          예) FAQ "유심 재발급 비용은 7,700원" / 질문 "카드로 결제되나요?" -> false
          예) FAQ "eSIM 발급 비용은 2,750원" / 질문 "부가세 포함인가요?" -> false

        판단이 애매하면 true를 고르십시오.

        JSON만 출력하십시오.
        {"answerable": true 또는 false}
        """;

    public static String userPrompt(String context, String question) {
        return """
            [FAQ]
            %s

            [고객 질문]
            %s""".formatted(context, question);
    }
}
