package com.telme.chat.service;

import com.telme.chat.service.BasicConversationClassifier.Type;

/** 기본 대화는 모델 호출 없이 서버에 정한 문구로 응답한다. */
public final class BasicConversationResponder {
    private BasicConversationResponder() {}

    public static String respond(Type type) {
        return switch (type) {
            case GREETING -> ChatPersona.GREETING;
            case THANKS -> ChatPersona.THANKS;
            case FAREWELL -> ChatPersona.FAREWELL;
            case CALL -> ChatPersona.CALL;
            case IDENTITY -> ChatPersona.IDENTITY;
            case HELP -> ChatPersona.HELP;
            case WELLBEING -> ChatPersona.WELLBEING;
            case NONE -> throw new IllegalArgumentException("기본 대화 유형이 필요합니다.");
        };
    }

    public static boolean isFixedResponse(String content) {
        if (content == null) return false;
        for (Type type : Type.values()) {
            if (type != Type.NONE && respond(type).equals(content.strip())) return true;
        }
        return false;
    }
}
