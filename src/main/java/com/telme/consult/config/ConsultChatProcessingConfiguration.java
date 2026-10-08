package com.telme.consult.config;

import com.telme.consult.service.ConsultChatPersistenceService;
import com.telme.consult.service.ConsultChatProcessingService;
import com.telme.consult.service.ChatEmitterConsultEvents;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.NoAnswerSuggestions;
import com.telme.consult.service.RagSearchResultAnswerGenerator.SuggestedQuestions;
import com.telme.consult.service.ConsultChatProcessingService.TurnAnalyzer;
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.service.ConsultChatEvents;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.repository.AskedQuestions;

import org.springframework.beans.factory.ObjectProvider;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Conditional(ConsultChatEnabledCondition.class)
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
            ConsultChatEvents events,
            ExecutionTrace trace,
            ObjectProvider<AskedQuestions> askedQuestions,
            ObjectProvider<NoAnswerSuggestions> noAnswerSuggestions,
            ObjectProvider<SuggestedQuestions> suggestedQuestions) {
        // 되묻기를 저장하지 않는 조합에서는 되물은 질문도 없다
        return new ConsultChatProcessingService(
                analyzer, answers, persistence, conditionConverter, events, trace,
                askedQuestions.getIfAvailable(AskedQuestions::none),
                noAnswerSuggestions.getIfAvailable(NoAnswerSuggestions::none),
                suggestedQuestions.getIfAvailable(SuggestedQuestions::none));
    }
}
