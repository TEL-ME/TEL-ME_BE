package com.telme.consult.config;

import com.telme.chat.repository.ChatMessageRepository;
import com.telme.consult.converter.FollowupConditionConverter;
import com.telme.consult.service.ConsultChatProcessingService.TurnAnalyzer;
import com.telme.consult.service.ConsultTurnAnalysisAdapter;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisProvider;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.ContextProvider;
import com.telme.consult.service.ConsultTurnPreparationService;
import com.telme.consult.service.QueryRoutingAnalysisProvider;
import com.telme.consult.service.QueryRoutingAnalysisProvider.FollowupAnalysisProvider;
import com.telme.consult.service.QueryRoutingFollowupAnalysisProvider;
import com.telme.intent.service.QueryRoutingService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 라우팅 결과를 상담 판단 입력으로 연결한다. 후속 분석 구현은 라우팅 모듈에서 제공한다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "telme.consult.chat-integration-enabled", havingValue = "true")
public class ConsultTurnRoutingConfiguration {
    @Bean
    @ConditionalOnMissingBean(FollowupAnalysisProvider.class)
    FollowupAnalysisProvider consultFollowupAnalysisProvider(QueryRoutingService routing) {
        return new QueryRoutingFollowupAnalysisProvider(routing);
    }

    @Bean
    @ConditionalOnMissingBean(AnalysisProvider.class)
    AnalysisProvider consultAnalysisProvider(
            ChatMessageRepository messages,
            QueryRoutingService routing,
            FollowupAnalysisProvider followups) {
        return new QueryRoutingAnalysisProvider(messages, routing, followups);
    }

    @Bean
    @ConditionalOnMissingBean(TurnAnalyzer.class)
    TurnAnalyzer consultTurnAnalyzer(
            ContextProvider contexts,
            AnalysisProvider analysis,
            ConsultTurnPreparationService preparation,
            FollowupConditionConverter followupConverter) {
        return new ConsultTurnAnalysisAdapter(
                contexts, analysis, preparation, followupConverter);
    }
}
