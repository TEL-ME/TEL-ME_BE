package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.llm.service.LlmClient;
import com.telme.llm.dto.req.LlmRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ChatQuestionResolverTest {
    private final LlmClient model = mock(LlmClient.class);
    private final ChatQuestionResolver resolver = new ChatQuestionResolver(model, new ObjectMapper(), ExecutionTrace.noop());
    private final ChatContextMessage source = new ChatContextMessage(1L, 1, ChatMessage.Role.USER,
            ChatMessage.MessageType.QUESTION, "해외 로밍 신청을 알아보고 있어요.", null);

    @Test
    void resolvesOnlyTheReferenceAndKeepsRequestConditionsAndNegation() throws Exception {
        var result = resolver.validate("그건 아직 신청 안 했는데 어떻게 신청해?",
                "{\"needsClarification\":false,\"sourceMessageIds\":[1]}", Map.of(1L, source));
        assertThat(result.question()).contains(source.content(), "그건 아직 신청 안 했는데 어떻게 신청해?");
        assertThat(result.sourceMessageIds()).containsExactly(1L);
    }

    @Test
    void rejectsNewTermsForeignSourcesAndChangesToTheRequestedAction() {
        for (String response : List.of("{\"needsClarification\":false,\"sourceMessageIds\":[99]}",
                "{\"needsClarification\":false,\"sourceMessageIds\":[1,1]}",
                "{\"needsClarification\":false,\"sourceMessageIds\":[1],\"replacement\":\"일본 로밍\"}")) {
            assertThatThrownBy(() -> resolver.validate("그건 신청 안 했어", response, Map.of(1L, source)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void missingHistoryAndUnresolvedPronounAskForClarification() {
        assertThat(resolver.resolve(new ChatProcessingCommand(1L, 1L, 1L, "그건 얼마야?"), null)
                .needsClarification()).isTrue();
        assertThat(resolver.resolve(new ChatProcessingCommand(1L, 1L, 1L, "비용은 얼마야?"), null)
                .needsClarification()).isTrue();
    }

    @Test
    void fullySpecifiedNewTopicDoesNotMakeAnExtraModelRequest() {
        var context = new ChatContext(1L, 5L, "로밍 상담", List.of(source), "유심 재발급 비용 알려줘", 100);
        assertThat(resolver.resolve(new ChatProcessingCommand(1L, 1L, 5L, context.currentQuestion()), context)
                .question()).isEqualTo(context.currentQuestion());
        verifyNoInteractions(model);
    }

    @Test
    void explicitAmbiguityDoesNotForceAPreviousTopic() throws Exception {
        var result = resolver.validate("그건?", "{\"needsClarification\":true,\"sourceMessageIds\":[]}", Map.of(1L, source));
        assertThat(result.needsClarification()).isTrue();
    }

    @Test
    void resolverInputContainsOnlyCustomerSourcesInDeterministicOrder() throws Exception {
        var answer = new ChatContextMessage(2L, 2, ChatMessage.Role.ASSISTANT,
                ChatMessage.MessageType.ANSWER, "로밍은 무조건 무료입니다.", null);
        var social = new ChatContextMessage(3L, 3, ChatMessage.Role.USER,
                ChatMessage.MessageType.QUESTION, "감사합니다", null);
        var correction = new ChatContextMessage(4L, 4, ChatMessage.Role.USER,
                ChatMessage.MessageType.QUESTION, "일본이 아니라 미국에 갑니다.", null);
        var context = new ChatContext(1L, 5L, null, List.of(source, answer, social, correction),
                "그건 어떻게 신청해?", 100);
        when(model.generate(any())).thenReturn("{\"needsClarification\":false,\"sourceMessageIds\":[1,4]}");
        var result = resolver.resolve(new ChatProcessingCommand(1L, 1L, 5L, context.currentQuestion()), context);
        assertThat(result.question()).contains(source.content(), correction.content(), context.currentQuestion());
        assertThat(result.question()).doesNotContain("무조건 무료", "감사합니다");
        var sent = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model).generate(sent.capture());
        var data = new ObjectMapper().readTree(sent.getValue().userPrompt());
        assertThat(data.fieldNames().next()).isEqualTo("sourceMessages");
        assertThat(data.path("sourceMessages")).hasSize(2);
        assertThat(data.path("sourceMessages").get(0).path("messageId").asLong()).isEqualTo(1);
        assertThat(data.path("sourceMessages").get(1).path("messageId").asLong()).isEqualTo(4);
    }

    @Test
    void invalidModelOutputAndMismatchedExecutionCannotForceAHistoryTopic() {
        var context = new ChatContext(1L, 5L, null, List.of(source), "그건 얼마야?", 100);
        when(model.generate(any())).thenReturn("{\"needsClarification\":false,\"sourceMessageIds\":[99]}");
        assertThat(resolver.resolve(new ChatProcessingCommand(1L, 1L, 5L, context.currentQuestion()), context)
                .needsClarification()).isTrue();
        assertThat(resolver.resolve(new ChatProcessingCommand(1L, 2L, 5L, context.currentQuestion()), context)
                .needsClarification()).isTrue();
    }
}
