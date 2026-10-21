package com.telme.intent.service;

import com.telme.chat.service.ChatContext;
import java.util.regex.Pattern;

// 명확한 단독 질문에 한해 LLM의 의도 오분류를 보정한다.
final class RoutingIntentCorrection {

    private static final Pattern GENERAL_STORE_HOURS = Pattern.compile(
            "^(?:매장|대리점)(?:은|는|에서|에)?.*(?:보통|일반적으로).*(?:영업|운영|몇\\s*시|문\\s*열)"
                    + "|^(?:평일|주말).{0,20}(?:매장|대리점)\\s*방문.*가능");
    private static final Pattern STORE_LOOKUP = Pattern.compile(
            "찾|어디|근처|가까운|주변|위치|주소|연락처|주차|지점|직영점");

    // 이름만으로 통신 부가서비스인 것. FAQ에 이름이 없어 모델과 검색이 놓치는 경우를 되살린다
    private static final Pattern ADD_ON_SERVICE = Pattern.compile("컬러링|통화\\s*연결음|벨소리|데이터\\s*(?:쉐어링|셰어링)");
    private static final Pattern ADD_ON_ACTION = Pattern.compile("해지|가입|신청|변경|취소|설정|요금|비용|어디서|어떻게");
    // 앞 문장의 잡담("역시 안되는구나 ㅋㅋ 그럼 ...")을 떼고 묻는 부분만 남긴다
    private static final Pattern LEADING_CHAT = Pattern.compile("^.*?(?:그럼|그러면|근데|그런데|그리고)\\s+(?=\\S)");

    private RoutingIntentCorrection() {
    }

    static boolean isGeneralStorePolicy(String question, ChatContext context, String location) {
        return isStandaloneQuestion(question, context)
                && (location == null || location.isBlank())
                && GENERAL_STORE_HOURS.matcher(question).find()
                && !STORE_LOOKUP.matcher(question).find();
    }

    // 부가서비스 이름과 업무(해지·가입 등)를 함께 물으면 검색할 질문을, 아니면 null을 돌려준다
    static String addOnServiceQuery(String question) {
        if (question == null) {
            return null;
        }
        String stripped = LEADING_CHAT.matcher(question.strip()).replaceFirst("");
        String query = ADD_ON_SERVICE.matcher(stripped).find() ? stripped : question.strip();
        return ADD_ON_SERVICE.matcher(query).find() && ADD_ON_ACTION.matcher(query).find() ? query : null;
    }

    private static boolean isStandaloneQuestion(String question, ChatContext context) {
        return question != null && (context == null
                || context.summary() == null && context.history().isEmpty());
    }
}
