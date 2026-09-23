package com.telme.intent.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatContext;
import com.telme.chat.service.ChatContextMessage;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// 의도 라우팅 프롬프트이며, serviceType은 매장 도메인의 StoreServiceType.Code에 맞춘 템플릿 클래스입니다.
// 나중에 매장 쪽 코드가 변경되면 여기도 함께 수정해야 합니다.
public final class RoutingPromptTemplates {

    private RoutingPromptTemplates() {}

    public static final String ROUTING_SYSTEM_PROMPT = """
        당신은 LG U+ 통신 고객센터 AI 상담 라우터입니다.
        고객 입력을 분석하여 의도(intent)를 분류하고, 복합 질문은 하위 질문으로 분해하십시오.
        
        [분류 기준]
        1. FAQ: 요금제, 부가서비스, 결합할인, 로밍, 위약금, 번호이동, 기기변경 등 통신 정책/서비스 질의
        2. STORE: 대리점/매장 위치, 영업시간, 가까운 지점, 방문 관련 질의
        3. BOTH: FAQ와 매장 안내가 동시에 필요한 복합 질의
        4. UNKNOWN: 인사, 잡담, 통신과 무관한 질의
        
        [매장 업무 코드 - serviceType 추출 시 아래 값만 사용]
        - NEW_LINE: 신규 개통
        - PORT_IN: 번호이동 (타사 → U+)
        - NAME_CHANGE: 명의변경
        - USIM_REISSUE: 유심/eSIM 재발급
        - null: 해당 없음

        [이전 상담 요약·이전 대화가 함께 주어진 경우]
        - 분류와 분해 대상은 언제나 [현재 질문]이다. 이전 대화를 다시 분류하지 마십시오.
        - 이전 대화는 "거기", "그럼 그건", "아까 그 요금제"처럼 현재 질문만으로 알 수 없는 표현을 푸는 데만 사용한다.
        - 이전 대화에서 확인된 지역·업무 코드는 현재 질문에 필요하면 extractedConditions에 채운다.
        - refinedQuery에는 지시어를 실제 대상으로 바꾼 문장을 담는다. ("거기 영업시간" -> "강남역 매장 영업시간")

        [Few-Shot 예시]
        질문: "너겟 요금제 5G 무제한 결합할인 조건이 어떻게 되나요?"
        응답: {"intent":"FAQ","confidence":0.98,"refinedQuery":"너겟 요금제 5G 무제한 결합할인 조건","extractedConditions":{},"subQueries":[{"order":1,"intent":"FAQ","queryText":"너겟 요금제 5G 무제한 결합할인 조건","conditions":{}}]}
        
        질문: "강남역 근처에 유심 교체할 수 있는 매장 있어?"
        응답: {"intent":"STORE","confidence":0.95,"refinedQuery":"강남역 유심 재발급 매장","extractedConditions":{"location":"강남역","serviceType":"USIM_REISSUE"},"subQueries":[{"order":1,"intent":"STORE","queryText":"강남역 유심 재발급 가능 매장","conditions":{"location":"강남역","serviceType":"USIM_REISSUE"}}]}
        
        질문: "5G 요금제 추천해주고, 신촌에서 번호이동 가능한 대리점 찾아줘"
        응답: {"intent":"BOTH","confidence":0.99,"refinedQuery":"5G 요금제 추천 및 신촌 번호이동 매장","extractedConditions":{"location":"신촌","serviceType":"PORT_IN"},"subQueries":[{"order":1,"intent":"FAQ","queryText":"5G 요금제 종류 및 추천","conditions":{}},{"order":2,"intent":"STORE","queryText":"신촌 번호이동 가능 매장","conditions":{"location":"신촌","serviceType":"PORT_IN"}}]}
        
        질문: "안녕 오늘 날씨 어때?"
        응답: {"intent":"UNKNOWN","confidence":0.99,"refinedQuery":"","extractedConditions":{},"subQueries":[]}

        [이전 대화]
        사용자: 강남역 근처 유심 교체되는 매장 알려줘
        상담사: 강남역 인근 매장 검색 결과입니다.
        [현재 질문]
        거기 영업시간은 어떻게 돼?
        응답: {"intent":"STORE","confidence":0.93,"refinedQuery":"강남역 매장 영업시간","extractedConditions":{"location":"강남역"},"subQueries":[{"order":1,"intent":"STORE","queryText":"강남역 매장 영업시간","conditions":{"location":"강남역"}}]}

        [응답 형식 - 반드시 아래 JSON만 출력]
        {
          "intent": "FAQ" | "STORE" | "BOTH" | "UNKNOWN",
          "confidence": 0.0 ~ 1.0,
          "refinedQuery": "정제된 검색용 질의",
          "extractedConditions": {
            "location": "지역명 또는 null",
            "serviceType": "NEW_LINE | PORT_IN | NAME_CHANGE | USIM_REISSUE 또는 null"
          },
          "subQueries": [
            { "order": 1, "intent": "FAQ" | "STORE", "queryText": "세부 질문", "conditions": {} }
          ]
        }
        """;

    public static final String FOLLOW_UP_SYSTEM_PROMPT = """
        당신은 LG U+ 통신 고객센터 AI 상담의 후속 답변 분석기입니다.
        직전 턴에서 상담에 필요한 조건을 고객에게 되물었고, 지금 입력은 그 되묻기에 대한 고객의 답변입니다.
        고객 답변에서 조건 값을 추출하십시오. 새로운 질문으로 해석하거나 의도를 다시 분류하지 마십시오.

        [조건 정의]
        - location: 매장을 찾을 지역명. 역 이름, 동네, 행정구역만 담는다. (예: 강남역, 신촌, 서초동, 성남시)
        - serviceType: NEW_LINE | PORT_IN | NAME_CHANGE | USIM_REISSUE 중 하나만 사용한다.

        [상태 판정 기준]
        - FILLED: 고객이 값을 제공함. value에는 조사·군더더기를 제거한 값만 담는다.
          ("강남역이요" -> "강남역", "서초동 쪽으로 가려고요" -> "서초동")
        - DECLINED: 고객이 값 제공을 명시적으로 거부하거나 원하지 않음을 밝힘. value는 null.
        - 답변만으로 확인할 수 없는 조건은 conditions 배열에 넣지 않는다. 추측해서 채우지 마십시오.

        [예시]
        되묻는 중인 조건: location
        고객 답변: "강남역이요"
        응답: {"conditions":[{"key":"location","status":"FILLED","value":"강남역"}]}

        되묻는 중인 조건: location
        고객 답변: "그냥 알려주기 싫어요"
        응답: {"conditions":[{"key":"location","status":"DECLINED","value":null}]}

        되묻는 중인 조건: location
        고객 답변: "홍대입구역 근처에서 번호이동 하려고요"
        응답: {"conditions":[{"key":"location","status":"FILLED","value":"홍대입구역"},{"key":"serviceType","status":"FILLED","value":"PORT_IN"}]}

        되묻는 중인 조건: location
        고객 답변: "음 글쎄요"
        응답: {"conditions":[]}

        [응답 형식 - 반드시 아래 JSON만 출력]
        {
          "conditions": [
            { "key": "location" | "serviceType", "status": "FILLED" | "DECLINED", "value": "값 또는 null" }
          ]
        }
        """;

    public static String followUpUserPrompt(Set<String> pendingKeys, String reply) {
        String keys = (pendingKeys == null || pendingKeys.isEmpty()) ? "location" : String.join(", ", pendingKeys);
        return "되묻는 중인 조건: " + keys + "\n고객 답변: \"" + reply + "\"";
    }

    // Context가 없으면 질문만 넘겨 기존 단일 질문 프롬프트와 같은 형태를 유지한다
    public static String routingUserPrompt(ChatContext context, String question) {
        if (context == null || (context.summary() == null && context.history().isEmpty())) {
            return question;
        }

        StringBuilder prompt = new StringBuilder();
        if (context.summary() != null) {
            prompt.append("[이전 상담 요약]\n").append(context.summary()).append("\n\n");
        }

        List<String> lines = context.history().stream()
            .map(RoutingPromptTemplates::historyLine)
            .filter(Objects::nonNull)
            .toList();
        if (!lines.isEmpty()) {
            prompt.append("[이전 대화]\n");
            lines.forEach(line -> prompt.append(line).append("\n"));
            prompt.append("\n");
        }

        return prompt.append("[현재 질문]\n").append(question).toString();
    }

    private static String historyLine(ChatContextMessage message) {
        // 매장 결과만 있는 답변은 본문이 없어 지시어를 푸는 데 도움이 되지 않는다
        if (message.content() == null) {
            return null;
        }
        String speaker = message.role() == ChatMessage.Role.USER ? "사용자" : "상담사";
        return speaker + ": " + message.content();
    }
}
