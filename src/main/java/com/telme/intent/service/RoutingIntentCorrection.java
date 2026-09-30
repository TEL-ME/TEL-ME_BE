package com.telme.intent.service;

import com.telme.chat.service.ChatContext;
import java.util.regex.Pattern;

/** 명확한 단독 질문에 한해 LLM의 의도 오분류를 보정한다. */
final class RoutingIntentCorrection {

    private static final Pattern TELECOM_TOPIC = Pattern.compile(
            "U\\+|유플러스|통신|휴대폰|핸드폰|스마트폰|요금제|로밍|유심|eSIM|USIM|개통|번호이동"
                    + "|기기변경|부가서비스|결합|약정|위약금|LTE|5G|데이터|인터넷|와이파이|매장|대리점"
                    + "|멤버십|청구|통화|문자|명의변경|단말|재발급",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXTERNAL_TOPIC = Pattern.compile(
            "다이어트|식단|요리|저녁\\s*메뉴|파이썬|코틀린|리스트\\s*정렬|강아지|고양이|사료"
                    + "|전세\\s*대출|주택담보대출|노트북|헬스장|원두|커피\\s*보관",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern GENERAL_STORE_HOURS = Pattern.compile(
            "^(?:매장|대리점)(?:은|는|에서|에)?.*(?:보통|일반적으로).*(?:영업|운영|몇\\s*시|문\\s*열)"
                    + "|^(?:평일|주말).{0,20}(?:매장|대리점)\\s*방문.*가능");
    private static final Pattern STORE_LOOKUP = Pattern.compile(
            "찾|어디|근처|가까운|주변|위치|주소|연락처|주차|지점|직영점");

    private RoutingIntentCorrection() {
    }

    static boolean isClearlyExternal(String question, ChatContext context) {
        if (!isStandaloneQuestion(question, context)) {
            return false;
        }
        return EXTERNAL_TOPIC.matcher(question).find()
                && !TELECOM_TOPIC.matcher(question).find();
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
