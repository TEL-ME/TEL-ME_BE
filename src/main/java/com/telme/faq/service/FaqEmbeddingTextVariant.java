package com.telme.faq.service;

// 임베딩에 넣을 텍스트를 만드는 4가지 방식
// 바꾸면 기존 벡터가 전부 무효이므로 전량 재임베딩이 따라와야 한다
public enum FaqEmbeddingTextVariant {

    QUESTION_ONLY {
        @Override
        public String assemble(String category, String question, String answer) {
            return question;
        }
    },

    // 테스트 기본값
    Q_A {
        @Override
        public String assemble(String category, String question, String answer) {
            return question + " " + answer;
        }
    },

    // 테스트 전 기준안
    CATEGORY_Q_A {
        @Override
        public String assemble(String category, String question, String answer) {
            return "[" + category + "] " + question + " " + answer;
        }
    },

    Q_A_HEAD200 {
        @Override
        public String assemble(String category, String question, String answer) {
            return question + " " + head(answer, ANSWER_HEAD_LIMIT);
        }
    };

    public static final int ANSWER_HEAD_LIMIT = 200;

    public abstract String assemble(String category, String question, String answer);

    // 서로게이트 쌍이 잘리지 않도록 코드포인트 기준으로 자른다
    static String head(String text, int limit) {
        if (text == null || text.codePointCount(0, text.length()) <= limit) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, limit));
    }
}
