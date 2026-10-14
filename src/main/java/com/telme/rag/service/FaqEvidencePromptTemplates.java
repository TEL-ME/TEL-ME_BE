package com.telme.rag.service;

// 회색 구간 FAQ 하나씩 판정하는 B3 프롬프트(TELME-122, TELME-156)
// 측정 수치(docs/GRAY_ZONE_UPPER_BAND.md)가 이 문장으로 나왔다. scripts/evidence_check_bedrock.py,
// scripts/gray_zone_upper_band_bedrock.py의 B3_SYSTEM_PROMPT와 글자까지 같아야 하며 테스트가 비교한다.
// 고치면 버전을 올리고 판정 시험 25건과 180셋 측정을 다시 한다
public final class FaqEvidencePromptTemplates {

    private FaqEvidencePromptTemplates() {}

    public static final String PROMPT_VERSION = "evidence-check-v3";

    public static final String SYSTEM_PROMPT = """
        당신은 통신사 FAQ 검색 결과를 검수합니다.
        고객 질문에 답할 정보가 FAQ에 있는지 아래 순서대로 판단하십시오.

        1. asked: 고객이 알고 싶어 하는 것을 항목별로 짧게 적습니다.
           질문에 나온 행위(신청, 해지, 변경 등)와 알고 싶은 정보의 종류(비용, 소요 시간, 마감 기한,
           방법, 장소, 가능 여부, 혜택, 기준 등)를 바꾸지 말고 그대로 적습니다.
           질문 문장을 통째로 옮기지 말고, 항목마다 무엇에 대한 것인지(상품, 서비스, 상태, 상황)를 함께 적습니다.
        2. found: asked의 각 항목이 FAQ에 있으면 true, 없으면 false를 같은 순서로 적습니다.
           - 행위가 다르면 없는 것입니다.
           - 대상(상품, 서비스, 상태, 상황)이 다르면 없는 것입니다. 이름이나 주제가 비슷해도 마찬가지입니다.
           - 정보의 종류가 다르면 없는 것입니다. 주제가 같아도 마찬가지입니다.
           - 묻는 값의 단위나 표현이 FAQ와 달라도, FAQ에 적힌 값을 환산하거나 계산해 답을 만들 수 있으면
             있는 것입니다. 이것은 정보의 종류가 다른 경우가 아닙니다.
        3. answerable: found에 true가 하나라도 있으면 true, 모두 false면 false.

        JSON만 출력하십시오.
        {"asked": ["..."], "found": [true 또는 false], "answerable": true 또는 false}
        """;

    // 실험과 같은 형식. FAQ는 한 번에 하나만 넣는다
    public static String userPrompt(String faqQuestion, String faqAnswer, String question) {
        return "[FAQ]\n[1] Q: " + faqQuestion + "\nA: " + faqAnswer + "\n\n[고객 질문]\n" + question;
    }
}
