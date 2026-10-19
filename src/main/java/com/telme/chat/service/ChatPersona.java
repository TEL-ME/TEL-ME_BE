package com.telme.chat.service;

/** 기본 대화 고정 응답과 인사 붙이기가 같은 정체성을 사용하도록 한곳에 둔다. */
public final class ChatPersona {
    public static final String NAME = "무러바라";
    public static final String GREETING = "안녕하세요! 무러바라예요 😊 무엇이 궁금하세요?";
    public static final String THANKS = "천만에요 😊 더 궁금한 점이 있으면 말씀해 주세요.";
    public static final String FAREWELL = "이용해 주셔서 감사해요 😊 궁금한 점이 생기면 다시 찾아주세요.";
    public static final String IDENTITY =
            "저는 TELME 통신 고객센터 AI 상담사 무러바라예요 😊 통신 서비스와 매장 정보를 안내해 드려요.";
    public static final String HELP =
            "요금제, 청구·납부, 유심, 로밍 등 통신 서비스와 매장 관련 질문을 도와드려요 😊 궁금한 내용을 말씀해 주세요.";
    public static final String WELLBEING =
            "궁금한 통신 정보를 안내할 준비가 되어 있어요 😊 무엇이 궁금하세요?";
    public static final String CALL = "네, 무러바라예요 😊 궁금한 내용을 말씀해 주세요.";

    private ChatPersona() {}

    public static String prependGreeting(String input, String output) {
        var conversation = BasicConversationClassifier.classify(input);
        if (conversation.isPure() || !ConsultationQuestion.analyze(input).hasGreeting()
                || BasicConversationClassifier.hasLeadingGreeting(output)) {
            return output;
        }
        return output == null || output.isBlank() ? "안녕하세요!" : "안녕하세요! " + output;
    }

    public static ChatAnswer decorate(String input, ChatAnswer answer) {
        return new ChatAnswer(answer.messageType(), prependGreeting(input, answer.content()), answer.answerBasis(),
                answer.followUps(), answer.storeResults(), answer.storeSearchContext());
    }
}
