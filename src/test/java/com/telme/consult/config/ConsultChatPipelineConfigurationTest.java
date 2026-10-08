package com.telme.consult.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatProcessingPort;
import com.telme.chat.service.ExecutionTrace;
import com.telme.consult.converter.ConfirmedConditionConverter;
import com.telme.consult.converter.FollowupConditionConverter;
import com.telme.consult.service.ConsultChatEvents;
import com.telme.consult.service.ComparisonEvidenceResolver;
import com.telme.consult.service.FaqCandidateEvidenceResolver;
import com.telme.rag.converter.AnswerContextConverter;
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
import com.telme.faq.dto.res.FaqSearchResponse;
import com.telme.faq.service.FaqSearchService;
import com.telme.intent.service.QueryRoutingService;
import com.telme.llm.service.LlmClient;
import com.telme.rag.service.AnswerGenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

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
                    .withBean(
                            FollowupAnalysisProvider.class,
                            () -> mock(FollowupAnalysisProvider.class))
                    .withBean(ContextProvider.class, () -> mock(ContextProvider.class))
                    .withBean(
                            ConsultTurnPreparationService.class,
                            () -> mock(ConsultTurnPreparationService.class))
                    .withBean(FollowupConditionConverter.class, FollowupConditionConverter::new)
                    .withBean(FaqSearchService.class, () -> mock(FaqSearchService.class))
                    .withBean(ComparisonEvidenceResolver.class,
                            () -> mock(ComparisonEvidenceResolver.class))
                    .withBean(FaqCandidateEvidenceResolver.class,
                            () -> mock(FaqCandidateEvidenceResolver.class))
                    .withBean(AnswerContextConverter.class, AnswerContextConverter::new)
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
    void faqClarificationIsEnabledByDefaultWhenLlmClientExists() {
        runner.withPropertyValues(
                        "telme.consult.chat-integration-enabled=true",
                        "telme.consult.persistence-enabled=true",
                        "telme.consult.rag-integration-enabled=true")
                .withBean(LlmClient.class, () -> mock(LlmClient.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AnswerProvider.class).clarifies()).isTrue();
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

    // 매장 버튼은 추천 질문 안의 옵션이다. 켜야 매장을 언급한 답변 뒤에 매장 찾기 버튼이 붙는다
    @ParameterizedTest
    @CsvSource({"false, ''", "true, 유심 재발급 가능한 매장을 알려주세요."})
    void storeChipFlagControlsStoreQuestion(boolean storeChip, String expected) {
        runner.withPropertyValues(
                        "telme.consult.chat-integration-enabled=true",
                        "telme.consult.persistence-enabled=true",
                        "telme.consult.rag-integration-enabled=true",
                        "telme.consult.suggested-questions.enabled=true",
                        "telme.consult.suggested-questions.store-chip-enabled=" + storeChip)
                .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
                .withBean(EmbeddingProperties.class,
                        () -> new EmbeddingProperties("bge-m3", 1024, null, null, null))
                .run(
                        context -> {
                            var top = new FaqSearchResponse(1L, null, "USIM", "유심 재발급 비용이 얼마예요?",
                                    "매장에서 바로 재발급할 수 있습니다.", 0.9, 1, LocalDate.of(2026, 10, 1), 1, "Q_A");
                            var actual = context.getBean(SuggestedQuestions.class)
                                    .suggest(ChatMessage.AnswerBasis.GROUNDED, List.of(top));
                            assertThat(actual).isEqualTo(expected.isEmpty() ? List.of() : List.of(expected));
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
