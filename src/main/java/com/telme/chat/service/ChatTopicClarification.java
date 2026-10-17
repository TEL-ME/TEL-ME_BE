package com.telme.chat.service;

/** 생성 경로와 요약 판별이 서로 다른 주제 확인 문구를 사용하지 않도록 한다. */
public final class ChatTopicClarification {
    public static final String MESSAGE = "어떤 내용에 대한 질문인지 확인이 필요합니다. "
            + "상품이나 상담 주제를 조금 더 구체적으로 알려주세요.";

    private ChatTopicClarification() {}

    public static boolean isMessage(String content) {
        return content != null && MESSAGE.equals(ConsultationQuestion.extract(content));
    }
}
