package com.telme.consult.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.consult.repository.SuggestedQuestionFaqFinder;
import com.telme.consult.service.ConsultChatEvents;
import com.telme.consult.service.ConsultChatProcessingService.NoAnswerSuggestions;
import com.telme.consult.service.CompoundPartSuggestions;
import com.telme.consult.service.QueryRoutingAnalysisProvider.CompoundQuestionSuggestions;
import com.telme.consult.service.FaqCandidateNoAnswerSuggestions;
import com.telme.consult.service.ComparisonEvidenceResolver;
import com.telme.consult.service.FaqCandidateEvidenceResolver;
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
import com.telme.faq.config.EmbeddingProperties;
import com.telme.faq.service.FaqSearchService;
import com.telme.intent.service.RuleBasedRoutingFallback;
import com.telme.rag.service.AnswerGenerator;
import com.telme.rag.converter.AnswerContextConverter;
import com.telme.llm.service.LlmStreamHandler;
import com.telme.chat.service.ExecutionTrace;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;


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

    // 연결표·FAQ 규칙. 아래 버튼 중 켜진 것이 있을 때만 기동 중에 한 번 읽고, 형식 오류면 기동이 실패한다
    @Bean
    @Lazy
    SuggestedQuestionRecommender suggestedQuestionRecommender() {
        return SuggestedQuestionRecommender.load(new ObjectMapper());
    }

    // 꺼져 있으면 DB와 연결표를 읽지 않는다
    @Bean
    SuggestedQuestions consultSuggestedQuestions(
            SuggestedQuestionProperties properties,
            ObjectProvider<SuggestedQuestionRecommender> recommender,
            ObjectProvider<JdbcTemplate> jdbc,
            ObjectProvider<EmbeddingProperties> embedding) {
        if (!properties.enabled()) {
            return SuggestedQuestions.none();
        }
        return new PolicyLinkSuggestedQuestions(
                recommender.getObject(),
                new SuggestedQuestionFaqFinder(jdbc.getObject(), embedding.getObject().model()),
                properties.storeChipEnabled());
    }

    // 추천 질문과 이 버튼이 모두 켜져 있을 때만 동작한다
    @Bean
    NoAnswerSuggestions consultNoAnswerSuggestions(
            SuggestedQuestionProperties properties,
            FaqSearchService searches,
            ObjectProvider<SuggestedQuestionRecommender> recommender) {
        if (!properties.enabled() || !properties.noAnswerEnabled()) {
            return NoAnswerSuggestions.none();
        }
        return new FaqCandidateNoAnswerSuggestions(searches, recommender.getObject());
    }

    // FAQ와 매장 찾기가 섞인 질문의 버튼. FAQ 버튼은 답을 못 할 때 버튼, 매장 버튼은 매장 칩 플래그를 따른다
    @Bean
    CompoundQuestionSuggestions consultCompoundQuestionSuggestions(
            SuggestedQuestionProperties properties,
            FaqSearchService searches,
            ObjectProvider<SuggestedQuestionRecommender> recommender,
            ObjectProvider<RuleBasedRoutingFallback> serviceTypes) {
        boolean faq = properties.enabled() && properties.noAnswerEnabled();
        boolean store = properties.enabled() && properties.storeChipEnabled();
        if (!faq && !store) {
            return CompoundQuestionSuggestions.none();
        }
        SuggestedQuestionRecommender data = recommender.getObject();
        return new CompoundPartSuggestions(
                faq ? new FaqCandidateNoAnswerSuggestions(searches, data) : null,
                store ? data : null,
                serviceTypes.getIfAvailable(RuleBasedRoutingFallback::new));
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
            ComparisonEvidenceResolver comparisonEvidence, FaqCandidateEvidenceResolver candidateEvidence,
            AnswerContextConverter sourceConverter, ChatStoreAnswerProvider storeAnswers,
            SuggestedQuestions suggestedQuestions) {
        return new PurposeRoutingAnswerProvider(
                new FaqSearchAnswerProvider(searches, answers, trace,
                        comparisonEvidence, candidateEvidence, sourceConverter, suggestedQuestions),
                storeAnswers::generate);
    }
}
