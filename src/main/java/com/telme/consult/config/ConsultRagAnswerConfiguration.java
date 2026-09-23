package com.telme.consult.config;

import com.telme.consult.service.ConsultChatEvents;
import com.telme.consult.service.FaqSearchAnswerProvider;
import com.telme.consult.service.FaqSearchAnswerProvider.SearchResultAnswerGenerator;
import com.telme.consult.service.RagSearchResultAnswerGenerator;
import com.telme.consult.service.RagSearchResultAnswerGenerator.StreamHandlerFactory;
import com.telme.consult.service.PurposeRoutingAnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.faq.service.FaqSearchService;
import com.telme.rag.service.AnswerGenerator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** FAQ 검색 결과를 RAG 답변으로 변환하는 후속 연결 설정이다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "telme.consult.rag-integration-enabled", havingValue = "true")
public class ConsultRagAnswerConfiguration {
    @Bean
    StreamHandlerFactory consultAnswerStreamHandlerFactory(ConsultChatEvents events) {
        return events::stream;
    }

    @Bean
    SearchResultAnswerGenerator consultRagAnswerGenerator(
            AnswerGenerator answers, StreamHandlerFactory streamHandlers) {
        return new RagSearchResultAnswerGenerator(answers, streamHandlers);
    }

    @Bean
    AnswerProvider consultAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers) {
        return new PurposeRoutingAnswerProvider(
                new FaqSearchAnswerProvider(searches, answers));
    }
}
