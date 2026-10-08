package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.config.ChatQuestionResolutionProperties;
import com.telme.llm.service.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class ChatQuestionResolutionModeTest {
    private final LlmClient model = mock(LlmClient.class);
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ResolutionConfiguration.class)
            .withBean(LlmClient.class, () -> model)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(ExecutionTrace.class, ExecutionTrace::noop);

    @Test
    void defaultModeKeepsRegexGate() {
        context.run(application -> {
            var result = application.getBean(ChatQuestionResolver.class)
                    .resolve(new ChatProcessingCommand(1L, 1L, 1L, "유심 재발급 비용 알려줘"), null);
            assertThat(result.needsClarification()).isFalse();
            verifyNoInteractions(model);
        });
    }

    @Test
    void configuredLlmAllModeCallsModelForStandaloneQuestion() {
        when(model.generate(any())).thenReturn("{\"relation\":\"SELF_CONTAINED\",\"selectedMessageIds\":[]}");
        context.withPropertyValues("chat.question-resolution.mode=LLM_ALL").run(application -> {
            var result = application.getBean(ChatQuestionResolver.class)
                    .resolve(new ChatProcessingCommand(1L, 1L, 1L, "유심 재발급 비용 알려줘"), null);
            assertThat(result.needsClarification()).isFalse();
            verify(model).generate(any());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ChatQuestionResolutionProperties.class)
    @Import(ChatQuestionResolver.class)
    static class ResolutionConfiguration {
    }
}
