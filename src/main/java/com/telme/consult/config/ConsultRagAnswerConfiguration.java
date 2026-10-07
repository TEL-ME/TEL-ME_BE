package com.telme.consult.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.service.ConsultChatEvents;
import com.telme.consult.service.ChatStoreAnswerProvider;
import com.telme.consult.service.FaqSearchAnswerProvider;
import com.telme.consult.service.FaqSearchAnswerProvider.SearchResultAnswerGenerator;
import com.telme.consult.service.RagSearchResultAnswerGenerator;
import com.telme.consult.service.RagSearchResultAnswerGenerator.StreamHandlerFactory;
import com.telme.consult.service.RagSearchResultAnswerGenerator.SuggestedQuestions;
import com.telme.consult.service.PolicyLinkSuggestedQuestions;
import com.telme.consult.service.SuggestedQuestionRecommender;
import com.telme.consult.service.PurposeRoutingAnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.faq.service.FaqSearchService;
import com.telme.rag.service.AnswerGenerator;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.chat.service.ExecutionTrace;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/** FAQ 검색 결과를 RAG 답변으로 변환하는 후속 연결 설정이다. */
@Configuration(proxyBeanMethods = false)
@Conditional(ConsultChatEnabledCondition.class)
@EnableConfigurationProperties(SuggestedQuestionProperties.class)
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

    // 꺼져 있으면 DB와 연결표를 읽지 않는다. 켜면 기동할 때 연결표를 읽고 형식 오류면 기동이 실패한다
    @Bean
    SuggestedQuestions consultSuggestedQuestions(
            SuggestedQuestionProperties properties, ObjectProvider<JdbcTemplate> jdbc) {
        if (!properties.enabled()) {
            return SuggestedQuestions.none();
        }
        JdbcTemplate template = jdbc.getObject();
        return new PolicyLinkSuggestedQuestions(
                SuggestedQuestionRecommender.load(new ObjectMapper()),
                faqId -> {
                    List<String> refs = template.query(
                            "SELECT policy_ref FROM faqs WHERE faq_id = ?",
                            (rs, rowNum) -> rs.getString(1),
                            faqId);
                    return refs.isEmpty() ? null : refs.getFirst();
                });
    }

    @Bean
    SearchResultAnswerGenerator consultRagAnswerGenerator(
            AnswerGenerator answers,
            StreamHandlerFactory streamHandlers,
            SuggestedQuestions suggestedQuestions) {
        return new RagSearchResultAnswerGenerator(answers, streamHandlers, suggestedQuestions);
    }

    @Bean
    AnswerProvider consultAnswerProvider(
            FaqSearchService searches, SearchResultAnswerGenerator answers, ExecutionTrace trace,
            ChatStoreAnswerProvider storeAnswers) {
        return new PurposeRoutingAnswerProvider(
                new FaqSearchAnswerProvider(searches, answers, trace), storeAnswers::generate);
    }
}
