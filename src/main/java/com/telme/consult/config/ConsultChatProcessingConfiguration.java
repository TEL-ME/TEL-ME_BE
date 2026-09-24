package com.telme.consult.config;

import com.telme.consult.service.ConsultChatPersistenceService;
import com.telme.consult.service.ConsultChatProcessingService;
import com.telme.consult.service.ChatEmitterConsultEvents;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.TurnAnalyzer;
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.service.ConsultChatEvents;
import com.telme.chat.service.ChatEmitterRegistry;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "telme.consult.chat-integration-enabled", havingValue = "true")
public class ConsultChatProcessingConfiguration {
    @Bean
    @ConditionalOnMissingBean(ConsultChatEvents.class)
    ConsultChatEvents consultChatEvents(ChatEmitterRegistry emitters) {
        return new ChatEmitterConsultEvents(emitters);
    }

    // 분석·답변 어댑터는 각 연결 이슈에서 제공한다. 이 설정은 Chat–상담 처리기만 등록한다.
    @Bean
    ConsultChatProcessingService consultChatProcessingService(
            TurnAnalyzer analyzer,
            AnswerProvider answers,
            ConsultChatPersistenceService persistence,
            ConfirmedConditionConverter conditionConverter,
            ConsultChatEvents events) {
        return new ConsultChatProcessingService(
                analyzer, answers, persistence, conditionConverter, events);
    }
}
