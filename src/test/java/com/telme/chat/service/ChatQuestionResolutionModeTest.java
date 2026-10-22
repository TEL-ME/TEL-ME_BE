package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.config.ChatQuestionResolutionLlmConfiguration;
import com.telme.chat.config.ChatQuestionResolutionProperties;
import com.telme.chat.entity.ChatMessage;
import com.telme.llm.service.LlmClient;
import java.util.List;
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
    void defaultModeSkipsModelWithoutHistory() {
        context.run(application -> {
            var result = application.getBean(ChatQuestionResolver.class)
                    .resolve(new ChatProcessingCommand(1L, 1L, 1L, "유심 재발급 비용 알려줘"), null);
            assertThat(result.needsClarification()).isFalse();
            verifyNoInteractions(model);
        });
    }

    @Test
    void configuredLlmAllModeCallsModelForStandaloneQuestion() {
        when(model.generate(any())).thenReturn("{\"reference\":\"EXPLICIT\"}");
        context.withPropertyValues("chat.question-resolution.mode=LLM_ALL").run(application -> {
            var result = application.getBean(ChatQuestionResolver.class)
                    .resolve(new ChatProcessingCommand(1L, 1L, 1L, "유심 재발급 비용 알려줘"), null);
            assertThat(result.needsClarification()).isFalse();
            verify(model).generate(any());
        });
    }

    @Test
    void historyGatedModeCallsModelForFollowUpWithoutRuleMatch() {
        when(model.generate(any())).thenReturn("{\"reference\":\"EXPLICIT\"}");
        context.run(application -> {
            var result = application.getBean(ChatQuestionResolver.class).resolve(FOLLOW_UP, withHistory());
            assertThat(result.needsClarification()).isFalse();
            verify(model).generate(any());
        });
    }

    @Test
    void historyGatedModeSkipsModelWhenOnlyGreetingPrecedes() {
        context.run(application -> {
            var greeting = new ChatContextMessage(501L, 1, ChatMessage.Role.USER,
                    ChatMessage.MessageType.QUESTION, "안녕하세요", "[]");
            var result = application.getBean(ChatQuestionResolver.class)
                    .resolve(FOLLOW_UP, new ChatContext(1L, 1L, null, List.of(greeting), FOLLOW_UP.content(), 100));
            assertThat(result.sourceMessageIds()).isEmpty();
            verifyNoInteractions(model);
        });
    }

    @Test
    void regexGatedModeSkipsModelForTheSameFollowUp() {
        context.withPropertyValues("chat.question-resolution.mode=REGEX_GATED").run(application -> {
            var result = application.getBean(ChatQuestionResolver.class).resolve(FOLLOW_UP, withHistory());
            assertThat(result.sourceMessageIds()).isEmpty();
            verifyNoInteractions(model);
        });
    }

    private static final ChatProcessingCommand FOLLOW_UP = new ChatProcessingCommand(
            1L, 1L, 1L, "그 회선 명의를 얼마 전에 제 앞으로 바꿨는데, 그래도 바로 할 수 있나요?");

    private static ChatContext withHistory() {
        var earlier = new ChatContextMessage(501L, 1, ChatMessage.Role.USER,
                ChatMessage.MessageType.QUESTION, "명의 변경하려면 뭐 필요해요?", "[]");
        return new ChatContext(1L, 1L, null, List.of(earlier), FOLLOW_UP.content(), 100);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ChatQuestionResolutionProperties.class)
    @Import({ChatQuestionResolver.class, ChatQuestionResolutionLlmConfiguration.class})
    static class ResolutionConfiguration {
    }
}
