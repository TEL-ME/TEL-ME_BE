package com.telme.consult.config;

import com.telme.consult.service.ConsultChatEvents;
import com.telme.consult.service.ChatStoreAnswerProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.service.ConditionExtractor;
import com.telme.consult.service.FaqClarificationPlanner;
import com.telme.consult.service.FaqSearchAnswerProvider;
import com.telme.llm.service.LlmClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.telme.consult.service.FaqSearchAnswerProvider.SearchResultAnswerGenerator;
import com.telme.consult.service.RagSearchResultAnswerGenerator;
import com.telme.consult.service.RagSearchResultAnswerGenerator.StreamHandlerFactory;
import com.telme.consult.service.PurposeRoutingAnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.faq.service.FaqSearchService;
import com.telme.rag.service.AnswerGenerator;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.chat.service.ExecutionTrace;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/** FAQ 검색 결과를 RAG 답변으로 변환하는 후속 연결 설정이다. */
@Configuration
@EnableConfigurationProperties(FaqClarificationProperties.class)
@Conditional(ConsultChatEnabledCondition.class)
public class ConsultRagAnswerConfiguration {
    @Bean
    StreamHandlerFactory consultAnswerStreamHandlerFactory(ConsultChatEvents events) {
        return executionId -> {
            LlmStreamHandler delegate = events.stream(executionId);
            return new LlmStreamHandler() {
                @Override
                public void onToken(String token) {
                    // 검증된 내용도 저장 전에는 전송하지 않는다. 최종 저장 후 처리기가 보낸다.
                    delegate.onProgress();
                }

                @Override
                public void onProgress() {
                    delegate.onProgress();
                }

                @Override
                public void onRetry(int attempt, Throwable cause) {
                    delegate.onRetry(attempt, cause);
                }

                @Override
                public void onComplete() {}

                @Override
                public void onError(Throwable error) {}
            };
        };
    }

    @Bean
    SearchResultAnswerGenerator consultRagAnswerGenerator(
            AnswerGenerator answers, StreamHandlerFactory streamHandlers) {
        return new RagSearchResultAnswerGenerator(answers, streamHandlers);
    }

    @Bean
    AnswerProvider consultAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers, ExecutionTrace trace,
            ChatStoreAnswerProvider storeAnswers, ObjectProvider<LlmClient> llmClient,
            FaqClarificationProperties clarification) {
        // 꺼져 있거나 모델을 쓸 수 없으면 되묻지 않고 지금처럼 바로 답한다
        LlmClient client = clarification.enabled() ? llmClient.getIfAvailable() : null;
        FaqClarificationPlanner planner = client == null
                ? null
                : new FaqClarificationPlanner(new ConditionExtractor(client, new ObjectMapper()));
        return new PurposeRoutingAnswerProvider(
                new FaqSearchAnswerProvider(searches, answers, trace, planner), storeAnswers::generate);
    }
}
