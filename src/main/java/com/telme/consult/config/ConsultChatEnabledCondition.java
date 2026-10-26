package com.telme.consult.config;

import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

// 채팅 처리(ConsultChatProcessingService)는 상담 저장과 RAG 답변 빈이 모두 있어야 만들 수 있다.
// 셋 중 하나라도 꺼져 있으면 채팅 관련 빈을 등록하지 않아, 기동은 되고 채팅은 AI_NOT_CONNECTED로 실패한다
class ConsultChatEnabledCondition extends AllNestedConditions {

    ConsultChatEnabledCondition() {
        super(ConfigurationPhase.REGISTER_BEAN);
    }

    @ConditionalOnProperty(name = "telme.consult.chat-integration-enabled", havingValue = "true")
    static class ChatIntegration {
    }

    @ConditionalOnProperty(name = "telme.consult.persistence-enabled", havingValue = "true")
    static class Persistence {
    }

    @ConditionalOnProperty(name = "telme.consult.rag-integration-enabled", havingValue = "true")
    static class RagIntegration {
    }
}
