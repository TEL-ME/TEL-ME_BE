package com.telme.consult.service;

/** 되묻기 문장 생성. 실제 모델 연결은 공통 LlmClient를 사용한다. */
@FunctionalInterface
public interface ClarificationTextGenerator {
    String generate(ClarificationPrompt prompt);

    // 문장이 같아도 모델과 고정 질문의 출처는 구분한다.
    default com.telme.consult.dto.DialogueDecision.MessageOrigin origin() {
        return com.telme.consult.dto.DialogueDecision.MessageOrigin.MODEL;
    }

    static ClarificationTextGenerator template() {
        return new ClarificationTextGenerator() {
            @Override
            public String generate(ClarificationPrompt prompt) {
                return prompt.fallbackText();
            }

            @Override
            public com.telme.consult.dto.DialogueDecision.MessageOrigin origin() {
                return com.telme.consult.dto.DialogueDecision.MessageOrigin.TEMPLATE;
            }
        };
    }

    record ClarificationPrompt(String systemPrompt, String userPrompt, String fallbackText) {}

    /** 고정 질문으로 전환할 수 있는 모델 오류. */
    class GenerationUnavailableException extends RuntimeException {
        public GenerationUnavailableException(String message) {
            super(message);
        }

        public GenerationUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
