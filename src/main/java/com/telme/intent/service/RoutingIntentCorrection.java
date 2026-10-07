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

    private RoutingIntentCorrection() {
    }

    static boolean isGeneralStorePolicy(String question, ChatContext context, String location) {
        return isStandaloneQuestion(question, context)
                && (location == null || location.isBlank())
                && GENERAL_STORE_HOURS.matcher(question).find()
                && !STORE_LOOKUP.matcher(question).find();
    }

    private static boolean isStandaloneQuestion(String question, ChatContext context) {
        return question != null && (context == null
                || context.summary() == null && context.history().isEmpty());
    }
}
