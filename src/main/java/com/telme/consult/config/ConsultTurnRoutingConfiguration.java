package com.telme.consult.config;

import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.service.ChatQuestionResolver;
import com.telme.consult.converter.FollowupConditionConverter;
import com.telme.consult.service.BasicConversationAnalysisProvider;
import com.telme.consult.service.ConsultChatProcessingService.TurnAnalyzer;
import com.telme.consult.service.ConsultTurnAnalysisAdapter;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisProvider;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.ContextProvider;
import com.telme.consult.service.ConsultTurnPreparationService;
import com.telme.consult.service.QueryRoutingAnalysisProvider;
import com.telme.consult.service.QueryRoutingAnalysisProvider.CompoundQuestionSuggestions;
import com.telme.consult.service.QueryRoutingAnalysisProvider.FollowupAnalysisProvider;
import com.telme.consult.service.QueryRoutingFollowupAnalysisProvider;
import com.telme.faq.dto.req.FaqSearchRequest;
import com.telme.faq.service.FaqSearchService;
import com.telme.intent.service.QueryRoutingService;
import com.telme.intent.service.UnknownFaqRecheck;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/** 라우팅 결과를 상담 판단 입력으로 연결한다. 후속 분석 구현은 라우팅 모듈에서 제공한다. */
@Configuration(proxyBeanMethods = false)
@Conditional(ConsultChatEnabledCondition.class)
public class ConsultTurnRoutingConfiguration {
    @Bean
    @ConditionalOnMissingBean(FollowupAnalysisProvider.class)
    FollowupAnalysisProvider consultFollowupAnalysisProvider(QueryRoutingService routing) {
        return new QueryRoutingFollowupAnalysisProvider(routing);
    }

    // 판정 없이 기존 통과 임계값(search.similarity-threshold, search.dual-vector)으로 원문을 검색한다.
    // 회색 구간(search.gray-zone)을 켜도 재확인 기준은 그대로라, 판정기로만 살릴 수 있는 점수의 질문은 UNKNOWN으로 남는다
    // (docs/GRAY_ZONE_UPPER_BAND.md 8.2절). 점수 기록은 남기지 않는다(kind 없음)
    @Bean
    @ConditionalOnMissingBean(UnknownFaqRecheck.class)
    UnknownFaqRecheck consultUnknownFaqRecheck(FaqSearchService searches) {
        return question -> !searches.search(new FaqSearchRequest(question, 1)).isEmpty();
    }

    @Bean
    @ConditionalOnMissingBean(AnalysisProvider.class)
    AnalysisProvider consultAnalysisProvider(
            ChatMessageRepository messages,
            QueryRoutingService routing,
            FollowupAnalysisProvider followups,
            ObjectProvider<CompoundQuestionSuggestions> compoundSuggestions) {
        return new BasicConversationAnalysisProvider(new QueryRoutingAnalysisProvider(messages, routing, followups,
                compoundSuggestions.getIfAvailable(CompoundQuestionSuggestions::none)));
    }

    @Bean
    @ConditionalOnMissingBean(TurnAnalyzer.class)
    TurnAnalyzer consultTurnAnalyzer(
            ContextProvider contexts,
            AnalysisProvider analysis,
            ConsultTurnPreparationService preparation,
            FollowupConditionConverter followupConverter,
            ChatQuestionResolver resolver) {
        return new ConsultTurnAnalysisAdapter(
                contexts, analysis, preparation, followupConverter, resolver);
    }
}
