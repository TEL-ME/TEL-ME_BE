package com.telme.consult.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.service.ChatQuestionResolver;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatProcessingPort;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.converter.FollowupConditionConverter;
import com.telme.consult.service.ConsultChatEvents;
import com.telme.consult.service.ConsultChatPersistenceService;
import com.telme.consult.service.ConsultChatProcessingService;
import com.telme.consult.service.ConsultChatProcessingService.AnswerProvider;
import com.telme.consult.service.ConsultChatProcessingService.TurnAnalyzer;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisProvider;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.ContextProvider;
import com.telme.consult.service.ConsultTurnPreparationService;
import com.telme.consult.service.PurposeRoutingAnswerProvider;
import com.telme.consult.service.QueryRoutingAnalysisProvider.FollowupAnalysisProvider;
import com.telme.consult.service.PolicyLinkSuggestedQuestions;
import com.telme.consult.service.RagSearchResultAnswerGenerator;
import com.telme.consult.service.RagSearchResultAnswerGenerator.SuggestedQuestions;
import com.telme.faq.config.EmbeddingProperties;
import com.telme.faq.service.FaqSearchService;
import com.telme.intent.service.QueryRoutingService;
import com.telme.rag.service.AnswerGenerator;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

class ConsultChatPipelineConfigurationTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            ConsultTurnRoutingConfiguration.class,
                            ConsultRagAnswerConfiguration.class,
                            ConsultStoreSearchConfiguration.class,
                            ConsultChatProcessingConfiguration.class)
                    .withBean(com.telme.store.service.StoreSearchService.class,
                            () -> mock(com.telme.store.service.StoreSearchService.class))
                    .withBean(com.telme.chat.converter.ChatStoreConverter.class,
                            com.telme.chat.converter.ChatStoreConverter::new)
                    .withBean(ChatMessageRepository.class, () -> mock(ChatMessageRepository.class))
                    .withBean(ChatEmitterRegistry.class, () -> mock(ChatEmitterRegistry.class))
                    .withBean(ExecutionTrace.class, () -> mock(ExecutionTrace.class))
                    .withBean(QueryRoutingService.class, () -> mock(QueryRoutingService.class))
                    .withBean(ChatQuestionResolver.class, () -> mock(ChatQuestionResolver.class))
                    .withBean(
                            FollowupAnalysisProvider.class,
                            () -> mock(FollowupAnalysisProvider.class))
                    .withBean(ContextProvider.class, () -> mock(ContextProvider.class))
                    .withBean(
                            ConsultTurnPreparationService.class,
                            () -> mock(ConsultTurnPreparationService.class))
                    .withBean(FollowupConditionConverter.class, FollowupConditionConverter::new)
                    .withBean(FaqSearchService.class, () -> mock(FaqSearchService.class))
                    .withBean(AnswerGenerator.class, () -> mock(AnswerGenerator.class))
                    .withBean(
                            ConsultChatPersistenceService.class,
                            () -> mock(ConsultChatPersistenceService.class))
                    .withBean(ConfirmedConditionConverter.class, ConfirmedConditionConverter::new);

    @Test
    void namedLocationImplementationReplacesUnavailableAdapter() {
        var named = mock(com.telme.consult.service.NamedLocationStoreSearchPort.class);
        runner.withPropertyValues(
                        "telme.consult.chat-integration-enabled=true",
                        "telme.consult.persistence-enabled=true",
                        "telme.consult.rag-integration-enabled=true")
                .withBean(com.telme.consult.service.NamedLocationStoreSearchPort.class, () -> named)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(com.telme.consult.service.NamedLocationStoreSearchPort.class);
                    assertThat(context.getBean(com.telme.consult.service.NamedLocationStoreSearchPort.class))
                            .isSameAs(named);
                });
    }

    @Test
    void enabledPipelineRegistersOneChatProcessingPortAndRealFaqRagAdapters() {
        runner.withPropertyValues(
                        "telme.consult.chat-integration-enabled=true",
                        "telme.consult.persistence-enabled=true",
                        "telme.consult.rag-integration-enabled=true")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(ChatProcessingPort.class);
                            assertThat(context).hasSingleBean(ConsultChatProcessingService.class);
                            assertThat(context).hasSingleBean(TurnAnalyzer.class);
                            assertThat(context).hasSingleBean(AnalysisProvider.class);
                            assertThat(context).hasSingleBean(AnswerProvider.class);
                            assertThat(context).hasSingleBean(PurposeRoutingAnswerProvider.class);
                            assertThat(context)
                                    .hasSingleBean(RagSearchResultAnswerGenerator.class);
                            assertThat(context).hasSingleBean(ConsultChatEvents.class);
                            assertThat(context.getBean(ConsultChatEvents.class))
                                    .isInstanceOf(
                                            com.telme.consult.service.ChatEmitterConsultEvents.class);
                        });
    }

    @Test
    void suggestedQuestionsAreOffByDefault() {
        runner.withPropertyValues(
                        "telme.consult.chat-integration-enabled=true",
                        "telme.consult.persistence-enabled=true",
                        "telme.consult.rag-integration-enabled=true")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(SuggestedQuestions.class))
                                    .isNotInstanceOf(PolicyLinkSuggestedQuestions.class);
                        });
    }

    // 켜면 기동할 때 연결표를 읽는다. 형식 오류면 여기서 기동이 실패한다
    @Test
    void enabledSuggestedQuestionsLoadPolicyLinks() {
        runner.withPropertyValues(
                        "telme.consult.chat-integration-enabled=true",
                        "telme.consult.persistence-enabled=true",
                        "telme.consult.rag-integration-enabled=true",
                        "telme.consult.suggested-questions.enabled=true")
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(EmbeddingProperties.class,
                        () -> new EmbeddingProperties("bge-m3", 1024, null, null, null))
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(SuggestedQuestions.class))
                                    .isInstanceOf(PolicyLinkSuggestedQuestions.class);
                        });
    }

    @Test
    void customSseEventsReplaceNoopEvents() {
        ConsultChatEvents events = mock(ConsultChatEvents.class);

        runner.withPropertyValues(
                        "telme.consult.chat-integration-enabled=true",
                        "telme.consult.persistence-enabled=true",
                        "telme.consult.rag-integration-enabled=true")
                .withBean(ConsultChatEvents.class, () -> events)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context).hasSingleBean(ConsultChatEvents.class);
                            assertThat(context.getBean(ConsultChatEvents.class)).isSameAs(events);
                        });
    }
}
