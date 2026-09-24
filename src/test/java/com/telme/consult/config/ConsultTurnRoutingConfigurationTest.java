package com.telme.consult.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.telme.chat.repository.ChatMessageRepository;
import com.telme.consult.converter.FollowupConditionConverter;
import com.telme.consult.service.ConsultChatProcessingService.TurnAnalyzer;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisProvider;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.ContextProvider;
import com.telme.consult.service.ConsultTurnPreparationService;
import com.telme.consult.service.QueryRoutingAnalysisProvider.FollowupAnalysisProvider;
import com.telme.intent.service.QueryRoutingService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ConsultTurnRoutingConfigurationTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withUserConfiguration(ConsultTurnRoutingConfiguration.class)
                    .withBean(ChatMessageRepository.class, () -> mock(ChatMessageRepository.class))
                    .withBean(QueryRoutingService.class, () -> mock(QueryRoutingService.class))
                    .withBean(ContextProvider.class, () -> mock(ContextProvider.class))
                    .withBean(
                            ConsultTurnPreparationService.class,
                            () -> mock(ConsultTurnPreparationService.class))
                    .withBean(
                            FollowupConditionConverter.class,
                            FollowupConditionConverter::new);

    @Test
    void integrationDisabledDoesNotRegisterRoutingAdapters() {
        runner.run(
                context -> {
                    assertThat(context).doesNotHaveBean(AnalysisProvider.class);
                    assertThat(context).doesNotHaveBean(TurnAnalyzer.class);
                });
    }

    @Test
    void integrationEnabledRegistersRoutingAdapters() {
        runner.withPropertyValues(
                        "telme.consult.chat-integration-enabled=true",
                        "telme.consult.persistence-enabled=true")
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(AnalysisProvider.class);
                            assertThat(context).hasSingleBean(FollowupAnalysisProvider.class);
                            assertThat(context).hasSingleBean(TurnAnalyzer.class);
                        });
    }
}
