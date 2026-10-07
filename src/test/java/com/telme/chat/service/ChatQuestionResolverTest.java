package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.config.ChatQuestionResolutionProperties.Mode;
import com.telme.chat.entity.ChatMessage;
import com.telme.llm.service.LlmClient;
import com.telme.llm.dto.req.LlmRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @ParameterizedTest
    @ValueSource(strings = {"그때 요금은 얼마야?", "앞서 말한 건 얼마야?", "이전에 물어본 거 얼마야?",
            "아까 얼마랬죠?", "이전에 문의한 건?", "그럼 신청 방법은?", "필요한 서류는?"})
    void everyDetectedReferenceWithoutASourceRequiresClarification(String question) throws Exception {
        assertThat(resolver.resolve(new ChatProcessingCommand(1L, 1L, 1L, question), null)
                .needsClarification()).isTrue();
        assertThat(resolver.validate(question, "{\"needsClarification\":false,\"sourceMessageIds\":[]}",
                Map.of(1L, source)).needsClarification()).isTrue();
        verifyNoInteractions(model);
    }

    @ParameterizedTest
    @ValueSource(strings = {"이전에 신청한 로밍 요금제를 해지하려면?", "아까 신청한 로밍 요금제를 해지하려면?",
            "그때 가입한 LTE 요금제 변경 방법은?", "앞서 신청한 유심 재발급을 취소할 수 있나요?",
            "이전에 로밍 요금제를 신청한 것이 맞는지 확인하려면?"})
    void temporalExpressionDoesNotRequireHistoryForASelfContainedQuestion(String question) throws Exception {
        var result = resolver.resolve(new ChatProcessingCommand(1L, 1L, 5L, question), null);
        assertThat(result.needsClarification()).isFalse();
        assertThat(result.question()).isEqualTo(question);
        assertThat(result.sourceMessageIds()).isEmpty();
        verifyNoInteractions(model);
    }

    @Test
    void selfContainedTemporalQuestionDoesNotCarryAnUnrelatedHistoryTopic() {
        String question = "이전에 신청한 유심 재발급을 취소할 수 있나요?";
        var context = new ChatContext(1L, 5L, null, List.of(source), question, 100);
        var result = resolver.resolve(new ChatProcessingCommand(1L, 1L, 5L, question), context);
        assertThat(result.needsClarification()).isFalse();
        assertThat(result.question()).isEqualTo(question);
        assertThat(resolver.contextFor(result, context)).isNull();
        verifyNoInteractions(model);
    }

    @Test
    void missingTemporalTargetStillRequiresClarificationAfterModelAnalysis() {
        when(model.generate(any())).thenReturn("{\"needsClarification\":true,\"sourceMessageIds\":[]}");
        assertThat(resolver.resolve(new ChatProcessingCommand(1L, 1L, 5L, "이전에 신청한 건?"), null)
                .needsClarification()).isTrue();
        verify(model).generate(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "{\"needsClarification\":false,\"sourceMessageIds\":[99]}"})
    void failedTemporalAnalysisCannotInventASource(String output) {
        when(model.generate(any())).thenReturn(output);
        assertThat(resolver.resolve(new ChatProcessingCommand(1L, 1L, 5L,
                "이전에 신청한 상품을 변경하려면?"), null).needsClarification()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"아까 로밍과 유심 중 그건 얼마야?", "이전에 물어본 로밍 요금은 얼마야?"})
    void mentioningATopicDoesNotBypassAnActualHistoryReference(String question) {
        when(model.generate(any())).thenReturn("{\"needsClarification\":true,\"sourceMessageIds\":[]}");
        var context = new ChatContext(1L, 5L, null, List.of(source), question, 100);
        assertThat(resolver.resolve(new ChatProcessingCommand(1L, 1L, 5L, question), context)
                .needsClarification()).isTrue();
        verify(model).generate(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"그럼 신청 방법은?", "필요한 서류는?", "기간은 얼마나 걸려?"})
    void implicitFollowupSelectsTheOriginalCustomerTopic(String question) {
        var context = new ChatContext(1L, 5L, null, List.of(source), question, 100);
        when(model.generate(any())).thenReturn("{\"needsClarification\":false,\"sourceMessageIds\":[1]}");
        var result = resolver.resolve(new ChatProcessingCommand(1L, 1L, 5L, question), context);
        assertThat(result.needsClarification()).isFalse();
        assertThat(result.question()).contains(source.content(), question);
        assertThat(result.sourceMessageIds()).containsExactly(1L);
    }

    @Test
    void downstreamContextContainsOnlySelectedCustomerSourcesAndRecalculatesTokens() {
        var unrelated = new ChatContextMessage(3L, 3, ChatMessage.Role.USER,
                ChatMessage.MessageType.QUESTION, "유심 재발급", null);
        var answer = new ChatContextMessage(2L, 2, ChatMessage.Role.ASSISTANT,
                ChatMessage.MessageType.ANSWER, "로밍은 무료", null);
        var context = new ChatContext(1L, 5L, "강남역이라는 미검증 요약", List.of(answer, unrelated),
                "그건 얼마야?", 100, List.of(source));
        var scoped = resolver.contextFor(new ChatQuestionResolver.Resolution("복원 질문", false, List.of(1L)), context);
        assertThat(scoped.history()).containsExactly(source);
        assertThat(scoped.summary()).isNull();
        assertThat(scoped.summarySources()).isEmpty();
        var estimator = new ChatTokenEstimator();
        assertThat(scoped.estimatedContextTokens()).isEqualTo(
                estimator.estimatePromptPart(context.currentQuestion()) + estimator.estimate(source));
        assertThat(resolver.contextFor(new ChatQuestionResolver.Resolution("독립 질문", false, List.of()), context))
                .isNull();
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

    @Test
    void llmAllAnalyzesQuestionsThatRegexGateWouldSkip() {
        var llmAll = new ChatQuestionResolver(model, new ObjectMapper(), ExecutionTrace.noop(), Mode.LLM_ALL);
        String question = "유심 재발급 비용 알려줘";
        when(model.generate(any())).thenReturn("{\"relation\":\"SELF_CONTAINED\",\"selectedMessageIds\":[]}");

        var result = llmAll.resolve(new ChatProcessingCommand(1L, 1L, 5L, question), null);

        assertThat(result.question()).isEqualTo(question);
        assertThat(result.needsClarification()).isFalse();
        var sent = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model).generate(sent.capture());
        assertThat(sent.getValue().promptVersion()).isEqualTo("multiturn-resolution-v8");
    }

    @Test
    void llmAllKeepsOriginalTopicAndCorrectedConditionInOrder() {
        var llmAll = new ChatQuestionResolver(model, new ObjectMapper(), ExecutionTrace.noop(), Mode.LLM_ALL);
        var correction = new ChatContextMessage(2L, 2, ChatMessage.Role.USER,
                ChatMessage.MessageType.QUESTION, "일본이 아니라 미국으로 갑니다.", null);
        String question = "그럼 신청 방법은?";
        var context = new ChatContext(1L, 5L, null, List.of(source, correction), question, 100);
        when(model.generate(any())).thenReturn(
                "{\"relation\":\"HISTORY_DEPENDENT\",\"selectedMessageIds\":[2,1]}");

        var result = llmAll.resolve(new ChatProcessingCommand(1L, 1L, 5L, question), context);

        assertThat(result.sourceMessageIds()).containsExactly(1L, 2L);
        assertThat(result.question()).contains(source.content(), correction.content(), question);
        assertThat(llmAll.contextFor(result, context).history()).containsExactly(source, correction);
    }

    @Test
    void llmAllRejectsUnknownSourcesAndInconsistentRelation() {
        var llmAll = new ChatQuestionResolver(model, new ObjectMapper(), ExecutionTrace.noop(), Mode.LLM_ALL);
        String question = "그럼 신청 방법은?";
        var context = new ChatContext(1L, 5L, null, List.of(source), question, 100);
        for (String response : List.of(
                "{\"relation\":\"HISTORY_DEPENDENT\",\"selectedMessageIds\":[99]}",
                "{\"relation\":\"HISTORY_DEPENDENT\",\"selectedMessageIds\":[]}",
                "{\"relation\":\"SELF_CONTAINED\",\"selectedMessageIds\":[1]}")) {
            when(model.generate(any())).thenReturn(response);
            assertThat(llmAll.resolve(new ChatProcessingCommand(1L, 1L, 5L, question), context)
                    .needsClarification()).isTrue();
        }
    }
}
