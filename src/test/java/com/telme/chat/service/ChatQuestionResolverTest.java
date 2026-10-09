package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.config.ChatQuestionResolutionProperties.Mode;
import com.telme.chat.entity.ChatMessage;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.entity.LlmGeneration.TaskType;
import com.telme.llm.service.LlmClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class ChatQuestionResolverTest {
    private final LlmClient model = mock(LlmClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatQuestionResolver resolver = new ChatQuestionResolver(model, mapper, ExecutionTrace.noop());
    private final ChatContextMessage source = customer(501L, 1, "해외 로밍 신청을 알아보고 있어요.");

    @Test
    void preservesOriginalRequestNegationAndResolvesOnlyCustomerSources() throws Exception {
        String question = "그건 아직 신청 안 했는데 어떻게 신청해?";
        var result = resolver.validateResolution(question, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"), Map.of(501L, source));
        assertThat(result.question()).contains(source.content(), question);
        assertThat(result.sourceMessageIds()).containsExactly(501L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"기간은 번호이동 신청 후 얼마나 걸리나요?", "컬러링을 해지하고 싶은데 그건 어디서 신청해요?",
            "요금 안 내면 언제 정지되나요?", "비용은 휴대폰 명의 변경 수수료를 알려주세요",
            "이전에 신청한 로밍 요금제를 해지하려면?", "아까 신청한 유심 재발급을 취소할 수 있나요?"})
    void explicitTargetKeepsOriginalTextWithoutAttachingUnrelatedHistory(String question) throws Exception {
        respond(model, explicit());
        var context = new ChatContext(1L, 5L, null, List.of(source), question, 100);
        var result = resolver.resolve(command(question), context);
        assertThat(result.needsClarification()).isFalse();
        assertThat(result.question()).isEqualTo(question);
        assertThat(result.sourceMessageIds()).isEmpty();
        assertThat(resolver.contextFor(result, context)).isNull();
        verify(model).generate(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"그건 얼마야?", "필요한 서류는?", "그때 요금은 얼마야?", "그럼 기간은요?"})
    void missingTargetWithNoHistoryRequiresClarification(String question) throws Exception {
        respond(model, omitted("[]"));
        assertThat(resolver.resolve(command(question), null).needsClarification()).isTrue();
        verify(model).generate(any());
    }

    @Test
    void twoTargetsInsideOneMessageRemainAmbiguous() throws Exception {
        var multiple = customer(501L, 1, "로밍과 컬러링 서비스를 알아보고 있어요");
        var result = resolver.validateResolution("그 서비스 비용은요?", omitted(
                "[{\"index\":1,\"targetCount\":2,\"parentIndex\":0}]"), Map.of(501L, multiple));
        assertThat(result.needsClarification()).isTrue();
        assertThat(result.sourceMessageIds()).isEmpty();
    }

    @Test
    void correctionLinksAlwaysIncludeOriginalBusinessAndLatestCorrection() throws Exception {
        var correction = customer(900L, 2, "대리인이 아니라 제가 직접 신청합니다");
        var original = customer(501L, 1, "유심 재발급을 대리인이 신청합니다");
        var result = resolver.validateResolution("그럼 비용은?", omitted(
                "[{\"index\":1,\"targetCount\":1,\"parentIndex\":0},"
                + "{\"index\":2,\"targetCount\":0,\"parentIndex\":1}]"), Map.of(900L, correction, 501L, original));
        assertThat(result.sourceMessageIds()).containsExactly(501L, 900L);
        assertThat(result.question()).contains(original.content(), correction.content(), "그럼 비용은?");
    }

    @Test
    void chainedCorrectionsKeepOriginalWorkAndNegatedConditionsInOrder() throws Exception {
        var second = customer(902L, 3, "미국이 아니라 캐나다입니다");
        var first = customer(900L, 2, "일본이 아니라 미국입니다");
        var original = customer(501L, 1, "일본 로밍을 신청합니다");
        var result = resolver.validateResolution("그럼 신청 방법은?", omitted(
                "[{\"index\":1,\"targetCount\":1,\"parentIndex\":0},"
                + "{\"index\":2,\"targetCount\":0,\"parentIndex\":1},"
                + "{\"index\":3,\"targetCount\":0,\"parentIndex\":2}]"), Map.of(902L, second, 900L, first, 501L, original));
        assertThat(result.sourceMessageIds()).containsExactly(501L, 900L, 902L);
        assertThat(result.question()).containsSubsequence(original.content(), first.content(), second.content());
    }

    @Test
    void latestIndependentTopicDoesNotCarryEarlierUnrelatedTask() throws Exception {
        var latest = customer(900L, 2, "유심 재발급을 신청합니다");
        var result = resolver.validateResolution("그럼 신청 방법은?", omitted(
                "[{\"index\":1,\"targetCount\":1,\"parentIndex\":0},"
                + "{\"index\":2,\"targetCount\":1,\"parentIndex\":0}]"), Map.of(501L, source, 900L, latest));
        assertThat(result.sourceMessageIds()).containsExactly(900L);
        assertThat(result.question()).doesNotContain("해외 로밍");
    }

    @Test
    void earlierReferencedBusinessKeepsItsCorrectionsAndExcludesNewerUnrelatedTopic() throws Exception {
        var correction = customer(902L, 3, "일본이 아니라 미국입니다");
        var unrelated = customer(900L, 2, "유심 재발급을 신청합니다");
        String raw = "{\"reference\":\"OMITTED\",\"anchorIndex\":1,\"referenceScope\":\"POSITION\",\"sources\":["
                + "{\"index\":1,\"targetCount\":1,\"parentIndex\":0},"
                + "{\"index\":2,\"targetCount\":1,\"parentIndex\":0},"
                + "{\"index\":3,\"targetCount\":0,\"parentIndex\":1}]}";
        var result = resolver.validateResolution("앞서 첫 번째로 물어본 건 신청 방법이 뭐야?", raw,
                Map.of(501L, source, 900L, unrelated, 902L, correction));
        assertThat(result.sourceMessageIds()).containsExactly(501L, 902L);
        assertThat(result.question()).contains(source.content(), correction.content()).doesNotContain(unrelated.content());
    }

    @Test
    void reorderedAssessmentsUseOriginalIndexesWithoutLosingALateCorrection() throws Exception {
        var correction = customer(902L, 3, "일본이 아니라 미국입니다");
        var unrelated = customer(900L, 2, "유심 재발급을 신청합니다");
        String raw = "{\"reference\":\"OMITTED\",\"anchorIndex\":1,\"referenceScope\":\"POSITION\",\"sources\":["
                + "{\"index\":1,\"targetCount\":1,\"parentIndex\":0},"
                + "{\"index\":3,\"targetCount\":0,\"parentIndex\":1},"
                + "{\"index\":2,\"targetCount\":1,\"parentIndex\":0}]}";
        var result = resolver.validateResolution("앞서 첫 번째로 물어본 건 신청 방법이 뭐야?", raw,
                Map.of(501L, source, 900L, unrelated, 902L, correction));
        assertThat(result.sourceMessageIds()).containsExactly(501L, 902L);
        assertThat(result.question()).contains(source.content(), correction.content()).doesNotContain(unrelated.content());
    }

    @Test
    void oneAnalyzedBusinessCanBeResolvedWithoutAnAnchorButMultipleBusinessesCannot() throws Exception {
        String single = "{\"reference\":\"OMITTED\",\"anchorIndex\":0,\"referenceScope\":\"RECENT\",\"sources\":[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]}";
        assertThat(resolver.validateResolution("그럼 신청 방법은?", single, Map.of(501L, source)).sourceMessageIds())
                .containsExactly(501L);
        String multiple = "{\"reference\":\"OMITTED\",\"anchorIndex\":0,\"referenceScope\":\"RECENT\",\"sources\":[{\"index\":1,\"targetCount\":2,\"parentIndex\":0}]}";
        assertThat(resolver.validateResolution("그건?", multiple, Map.of(501L, source)).needsClarification()).isTrue();
    }

    @Test
    void currentComparisonWithTwoExplicitTasksIsIndependent() throws Exception {
        String question = "요금 아끼려면 정지가 나아요, 해지가 나아요?";
        var result = resolver.validateResolution(question,
                "{\"reference\":\"EXPLICIT\",\"sources\":[],\"anchorIndex\":0,\"referenceScope\":\"RECENT\"}", Map.of());
        assertThat(result.needsClarification()).isFalse();
        assertThat(result.question()).isEqualTo(question);
    }

    @Test
    void explicitAmbiguityCannotAttachPreviousTopic() throws Exception {
        var result = resolver.validateResolution("로밍과 유심 중 그건 얼마야?",
                "{\"reference\":\"AMBIGUOUS\",\"sources\":[],\"anchorIndex\":0,\"referenceScope\":\"RECENT\"}", Map.of(501L, source));
        assertThat(result.needsClarification()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{}", "{\"reference\":\"OTHER\",\"sources\":[]}",
            "{\"reference\":\"OMITTED\",\"sources\":[]}",
            "{\"reference\":\"OMITTED\",\"sources\":[{\"index\":2,\"targetCount\":1,\"parentIndex\":0}]}",
            "{\"reference\":\"OMITTED\",\"sources\":[{\"index\":1,\"targetCount\":1,\"parentIndex\":1}]}",
            "{\"reference\":\"OMITTED\",\"sources\":[{\"index\":1,\"targetCount\":-1,\"parentIndex\":0}]}",
            "{\"reference\":\"OMITTED\",\"sources\":[{\"index\":1,\"targetCount\":1.5,\"parentIndex\":0}]}",
            "{\"reference\":\"OMITTED\",\"sources\":[{\"index\":1,\"targetCount\":999999999999,\"parentIndex\":0}]}",
            "{\"reference\":\"OMITTED\",\"sources\":[{\"index\":1,\"targetCount\":1,\"parentIndex\":-1}]}",
            "{\"reference\":\"OMITTED\",\"sources\":[{\"index\":1,\"targetCount\":1,\"parentIndex\":999999999999}]}"})
    void malformedCountsSourcesAndReferenceFailSafely(String response) throws Exception {
        String question = "그건 얼마야?";
        respond(model, response);
        var context = new ChatContext(1L, 5L, null, List.of(source), question, 100);
        assertThat(resolver.resolve(command(question), context).needsClarification()).isTrue();
    }

    @Test
    void sourceCannotLinkToAStatementWithNoBusinessOrParent() {
        var correction = customer(900L, 2, "일본이 아니라 미국입니다");
        assertThatThrownBy(() -> resolver.validateResolution("그건?", omitted(
                "[{\"index\":1,\"targetCount\":0,\"parentIndex\":0},"
                + "{\"index\":2,\"targetCount\":0,\"parentIndex\":1}]"), Map.of(501L, source, 900L, correction)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyCustomerTextAndInvalidStoredIdentifierCannotBeRestored() {
        for (var invalid : List.of(customer(0L, 1, "로밍"),
                new ChatContextMessage(501L, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION, "", "[]"),
                new ChatContextMessage(501L, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION, " ", "[]"),
                new ChatContextMessage(501L, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION, null, "[]"))) {
            assertThatThrownBy(() -> resolver.validateResolution("그건?",
                    omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"), Map.of(invalid.messageId(), invalid)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void onlyCustomerWorkMessagesAreSentAndSourceOrderingIsDeterministic() throws Exception {
        var assistant = new ChatContextMessage(502L, 2, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER, "로밍은 무료", null);
        var social = customer(503L, 3, "감사합니다");
        String question = "그건 얼마야?";
        respond(model, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"));
        var context = new ChatContext(1L, 5L, "미검증 요약", List.of(social, assistant), question, 100, List.of(source));
        assertThat(resolver.resolve(command(question), context).sourceMessageIds()).containsExactly(501L);
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model, times(2)).generate(request.capture());
        var inputs = mapper.readTree(request.getValue().userPrompt());
        assertThat(inputs.path("sourceMessages")).hasSize(1);
        assertThat(inputs.path("sourceMessages").get(0).path("content").asText()).isEqualTo(source.content());
        assertThat(request.getValue().userPrompt()).doesNotContain("로밍은 무료", "감사합니다", "미검증 요약");
    }

    @Test
    void missingCorrectionAssessmentIsRejectedInsteadOfPartiallyRestoringHistory() {
        var correction = customer(900L, 2, "일본이 아니라 미국입니다");
        assertThatThrownBy(() -> resolver.validateResolution("그건?", omitted(
                "[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"), Map.of(501L, source, 900L, correction)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sourceOrderIsLocalAndStableRegardlessOfDatabaseIdentifiers() throws Exception {
        respond(model, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"));
        String question = "그건 얼마야?";
        var context = new ChatContext(1L, 5L, null, List.of(source), question, 100);
        resolver.resolve(command(question), context);
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model, times(2)).generate(request.capture());
        var data = mapper.readTree(request.getValue().userPrompt());
        assertThat(data.path("sourceMessages").get(0).path("index").asInt()).isEqualTo(1);
        assertThat(data.path("sourceMessages").get(0).has("messageId")).isFalse();
        assertThat(request.getValue().taskType()).isEqualTo(TaskType.CONTEXT_RESOLUTION);
        assertThat(request.getValue().promptVersion()).isEqualTo("multiturn-resolution-v30-sources");
    }

    @Test
    void downstreamContextIncludesOnlyChosenCustomerSources() {
        var assistant = new ChatContextMessage(2L, 2, ChatMessage.Role.ASSISTANT, ChatMessage.MessageType.ANSWER, "로밍은 무료", null);
        var context = new ChatContext(1L, 5L, "미검증 요약", List.of(assistant), "그건?", 100, List.of(source));
        var scoped = resolver.contextFor(new ChatQuestionResolver.Resolution("복원 질문", false, List.of(501L)), context);
        assertThat(scoped.history()).containsExactly(source);
        assertThat(scoped.summary()).isNull();
        assertThat(scoped.summarySources()).isEmpty();
        assertThat(scoped.estimatedContextTokens()).isEqualTo(new ChatTokenEstimator().estimatePromptPart("그건?") + new ChatTokenEstimator().estimate(source));
    }

    @Test
    void llmAllUsesSameEvidenceContractForQuestionsOutsideRegexGate() throws Exception {
        var all = new ChatQuestionResolver(model, mapper, ExecutionTrace.noop(), Mode.LLM_ALL);
        String question = "유심 재발급 방법 알려줘";
        respond(model, explicit());
        assertThat(all.resolve(command(question), null).question()).isEqualTo(question);
        verify(model).generate(any());
    }

    @Test
    void currentReferenceRequestContainsNoPreviousTopic() throws Exception {
        String question = "요금 납부 방법 알려줘";
        respond(model, explicit());
        resolver.resolve(command(question), new ChatContext(1L, 5L, null, List.of(source), question, 100));
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model).generate(request.capture());
        assertThat(mapper.readTree(request.getValue().userPrompt()).size()).isEqualTo(1);
        assertThat(request.getValue().userPrompt()).doesNotContain(source.content());
    }

    @Test
    void secondModelFailureCannotRestoreOnlyPartOfHistory() {
        when(model.generate(any())).thenReturn("{\"reference\":\"OMITTED\"}")
                .thenThrow(new IllegalStateException("이전 발언 분석 실패"));
        String question = "그건 얼마야?";
        var result = resolver.resolve(command(question), new ChatContext(1L, 5L, null, List.of(source), question, 100));
        assertThat(result.needsClarification()).isTrue();
        assertThat(result.sourceMessageIds()).isEmpty();
        assertThat(result.question()).isEqualTo(question);
    }

    @Test
    void trailingOutputCannotOverrideOrHideAnInvalidDecision() {
        when(model.generate(any())).thenReturn("{\"reference\":\"EXPLICIT\"}{\"reference\":\"OMITTED\"}");
        assertThat(resolver.resolve(command("요금 안 내면 언제 정지되나요?"), null).needsClarification()).isTrue();
    }

    static void respond(LlmClient client, String combined) throws Exception {
        var mapper = new ObjectMapper();
        com.fasterxml.jackson.databind.JsonNode root;
        try { root = mapper.readTree(combined); } catch (Exception bad) { root = null; }
        var parsed = root;
        when(client.generate(any())).thenAnswer(call -> {
            LlmRequest request = call.getArgument(0);
            if (parsed == null || !parsed.isObject() || parsed.size() < 2 || parsed.size() > 4 || !parsed.has("reference") || !parsed.has("sources")) return combined;
            var result = mapper.createObjectNode();
            if (request.promptVersion().endsWith("-reference")) result.set("reference", parsed.get("reference"));
            else {
                result.set("sources", parsed.get("sources"));
                result.put("referenceScope", parsed.path("referenceScope").asText("RECENT"));
                result.put("anchorIndex", parsed.has("anchorIndex") ? parsed.get("anchorIndex").asInt() : parsed.get("sources").size());
            }
            return mapper.writeValueAsString(result);
        });
    }
    static String explicit() throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("reference", "EXPLICIT", "sources", List.of(), "anchorIndex", 0, "referenceScope", "RECENT"));
    }
    static String omitted(String sources) {
        try {
            int count = new ObjectMapper().readTree(sources).size();
            return "{\"reference\":\"OMITTED\",\"sources\":" + sources + ",\"anchorIndex\":" + count + ",\"referenceScope\":\"RECENT\"}";
        } catch (Exception error) { throw new IllegalArgumentException(error); }
    }
    static ChatProcessingCommand command(String question) { return new ChatProcessingCommand(1L, 1L, 5L, question); }
    static ChatContextMessage customer(long id, int sequence, String content) {
        return new ChatContextMessage(id, sequence, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION, content, null);
    }
}
