package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.config.ChatQuestionResolutionProperties.Mode;
import com.telme.chat.entity.ChatMessage;
import com.telme.global.common.exception.GeneralException;
import com.telme.llm.dto.req.LlmRequest;
import com.telme.llm.exception.LlmErrorCode;
import com.telme.llm.exception.LlmStreamCancelledException;
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
    // 흔들림 보완(chat.question-resolution.stability-guards)을 켠 판정기
    private final ChatQuestionResolver guarded = new ChatQuestionResolver(model, mapper, ExecutionTrace.noop(),
            Mode.REGEX_GATED, true);
    private final ChatContextMessage source = customer(501L, 1, "해외 로밍 신청을 알아보고 있어요.");

    @ParameterizedTest
    @ValueSource(strings = {"문자는요?", "로밍도요?", "그럼 데이터는요?", "미국은요?"})
    void topicOnlyFollowUpSkipsTheReferenceModel(String original) throws Exception {
        respond(model, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"));
        var context = new ChatContext(1L, 5L, null, List.of(source), original, 100);

        var result = resolver.resolve(command(original), context);

        assertThat(result.sourceMessageIds()).containsExactly(501L);
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model).generate(request.capture());
        assertThat(request.getValue().promptVersion()).isEqualTo("context-v32-sources");
    }

    @ParameterizedTest
    @ValueSource(strings = {"유심 가격은요?", "멤버십 혜택은요?", "그 서비스는요?", "한 20기가 정도요", "50GB 정도요", "마흔 정도요"})
    void followUpWithItemOrReferenceStillGoesThroughTheReferenceModel(String original) throws Exception {
        respond(model, explicit());
        var context = new ChatContext(1L, 5L, null, List.of(source), original, 100);

        var result = resolver.resolve(command(original), context);

        assertThat(result.sourceMessageIds()).isEmpty();
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model).generate(request.capture());
        assertThat(request.getValue().promptVersion()).isEqualTo("context-v32-reference");
    }

    @ParameterizedTest
    @ValueSource(strings = {"바라야 그럼 신청 방법은?", "안녕 바라야, 그럼 신청 방법은?",
            "그럼 신청 방법은? 고마워 바라야"})
    void addressedFollowUpStillResolvesTheSameCustomerSource(String original) throws Exception {
        respond(model, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"));
        var context = new ChatContext(1L, 5L, null, List.of(source), original, 100);

        var result = resolver.resolve(command(original), context);

        assertThat(result.needsClarification()).isFalse();
        assertThat(result.sourceMessageIds()).containsExactly(501L);
        assertThat(result.question()).endsWith("[현재 후속 질문]\n그럼 신청 방법은?");
        assertThat(context.currentQuestion()).isEqualTo(original);
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model).generate(request.capture());
        assertThat(mapper.readTree(request.getValue().userPrompt()).path("currentQuestion").asText())
                .isEqualTo("그럼 신청 방법은?");
    }

    @ParameterizedTest
    @ValueSource(strings = {"아 정정할게요 중학생 아니고 초등학교 6학년이에요", "아 아들 아니고 딸이요", "아 잘못말했다 19살이요",
            "아 확인해보니 24개월 아니고 36개월이었네요", "아 아니다 올리는게 아니라 20만원으로 낮출래요 애가 자꾸 결제해서",
            "아 형 아니고 동생이요 고1인데 미성년자도 되나? 그리고 분당 서현역 근처 매장도", "아 가입한 게 아니라 해지였어요",
            "아 로밍 아니고 유심 말이에요"})
    void correctionWithHistoryIsJudgedByTheModel(String original) throws Exception {
        respond(model, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"));
        var context = new ChatContext(1L, 5L, null, List.of(source), original, 100);

        var result = resolver.resolve(command(original), context);

        assertThat(result.sourceMessageIds()).containsExactly(501L);
        verify(model, org.mockito.Mockito.atLeastOnce()).generate(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LTE 말고 5G 쓰고 있어요 정정할게요",
            "아 정정할게요 유심 말고 eSIM 쓰면 돼요", "무제한 아니고 데이터 50GB 쓰고 있어요 정정할게요"})
    void correctionWithServiceWordSkipsTheReferenceModelAndLinksTheHistory(String original) throws Exception {
        respond(model, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"));
        var context = new ChatContext(1L, 5L, null, List.of(source), original, 100);

        var result = resolver.resolve(command(original), context);

        assertThat(result.sourceMessageIds()).containsExactly(501L);
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model).generate(request.capture());
        assertThat(request.getValue().promptVersion()).isEqualTo("context-v32-sources");
    }

    private static final String CORRECTION_QUESTION = "LTE 말고 5G 쓰고 있어요 정정할게요";

    private static String sourcesWithExtraEntry(int targetCount, int index) {
        return "{\"reference\":\"OMITTED\",\"sources\":[{\"index\":1,\"targetCount\":1,\"parentIndex\":0},"
                + "{\"index\":" + index + ",\"targetCount\":" + targetCount + ",\"parentIndex\":1}],"
                + "\"anchorIndex\":0,\"referenceScope\":\"RECENT\"}";
    }

    @Test
    void guardedResolverDropsTheCurrentQuestionEntryTheModelAppended() throws Exception {
        respond(model, sourcesWithExtraEntry(0, 2));
        var context = new ChatContext(1L, 5L, null, List.of(source), CORRECTION_QUESTION, 100);

        var result = guarded.resolve(command(CORRECTION_QUESTION), context);

        assertThat(result.needsClarification()).isFalse();
        assertThat(result.sourceMessageIds()).containsExactly(501L);
        verify(model, org.mockito.Mockito.times(1)).generate(any());
    }

    @Test
    void unguardedResolverStillRejectsTheExtraEntry() throws Exception {
        respond(model, sourcesWithExtraEntry(0, 2));
        var context = new ChatContext(1L, 5L, null, List.of(source), CORRECTION_QUESTION, 100);

        var result = resolver.resolve(command(CORRECTION_QUESTION), context);

        assertThat(result.needsClarification()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void guardedResolverKeepsRejectingOtherExtraEntries(int variant) throws Exception {
        // 새 업무로 센 항목(targetCount 1)과 번호가 어긋난 항목(3)은 현재 질문 항목이 아님
        respond(model, variant == 1 ? sourcesWithExtraEntry(1, 2) : sourcesWithExtraEntry(0, 3));
        var context = new ChatContext(1L, 5L, null, List.of(source), CORRECTION_QUESTION, 100);

        var result = guarded.resolve(command(CORRECTION_QUESTION), context);

        assertThat(result.needsClarification()).isTrue();
        verify(model, org.mockito.Mockito.times(2)).generate(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"명의 정정하고 싶어요", "가입 정보 정정하려고 합니다", "청구서 주소 정정하고 싶어요",
            "요금제를 잘못알고 가입해서 해지하고 싶어요",
            "결제 얘기가 아니라 폰 액정이 깨졌는데 보험 있으면 얼마 내요?", "엄마 명의가 아니라 제 명의인데 해지 돼요",
            "LTE 아니고 5G 요금제 있어요", "5G 아니고 LTE 쓰고 있어요"})
    void correctionWordInANewRequestStillGoesToTheReferenceModel(String original) throws Exception {
        respond(model, explicit());
        var context = new ChatContext(1L, 5L, null, List.of(source), original, 100);

        var result = resolver.resolve(command(original), context);

        assertThat(result.sourceMessageIds()).isEmpty();
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model).generate(request.capture());
        assertThat(request.getValue().promptVersion()).isEqualTo("context-v32-reference");
    }

    @ParameterizedTest
    @ValueSource(strings = {"LTE가 아니라 5G 요금제 뭐 있어요?", "5G 아니고 LTE 요금제는 얼마예요"})
    void correctionWordInsideACompleteQuestionStillGoesToTheReferenceDecision(String original) {
        var context = new ChatContext(1L, 5L, null, List.of(source), original, 100);

        var result = resolver.resolve(command(original), context);

        assertThat(result.sourceMessageIds()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(model);
    }

    @ParameterizedTest
    @ValueSource(strings = {"이번 달에 가입한 게 아니라면 요금제 바꿀 수 있는지 알려주세요", "LTE가 아니라 5G 요금제 뭐 있어요", "5G가 아니고 LTE 요금제는 얼마예요"})
    void completeQuestionWithCorrectionWordIsNotSentToTheModel(String original) {
        var context = new ChatContext(1L, 5L, null, List.of(source), original, 100);

        var result = resolver.resolve(command(original), context);

        assertThat(result.sourceMessageIds()).isEmpty();
        verify(model, org.mockito.Mockito.never()).generate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void correctionWithoutHistoryDoesNotCallTheModel() {
        var context = new ChatContext(1L, 5L, null, List.of(), "아 아들 아니고 딸이요", 100);

        var result = resolver.resolve(command("아 아들 아니고 딸이요"), context);

        assertThat(result.sourceMessageIds()).isEmpty();
        verify(model, org.mockito.Mockito.never()).generate(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolutionTraceKeepsOriginalAlongsideAnalysisBody() {
        var trace = mock(ExecutionTrace.class);
        var traced = new ChatQuestionResolver(model, mapper, trace);
        String original = "바라야 근처 매장 알려줘";
        traced.resolve(command(original), null);
        var value = ArgumentCaptor.forClass(Object.class);
        verify(trace).stage(org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq("questionResolution"), value.capture());
        assertThat((Map<?, ?>) value.getValue()).extracting("originalQuery", "analysisQuery")
                .containsExactly(original, "근처 매장 알려줘");
    }

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
        assertThat(resolver.resolve(command(question), null).needsClarification()).isTrue();
        // 대상도 이전 발언도 없으면 모델을 부르지 않고 확인 안내로 끝낸다.
        org.mockito.Mockito.verifyNoInteractions(model);
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
        verify(model).generate(request.capture());
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
        verify(model).generate(request.capture());
        var data = mapper.readTree(request.getValue().userPrompt());
        assertThat(data.path("sourceMessages").get(0).path("index").asInt()).isEqualTo(1);
        assertThat(data.path("sourceMessages").get(0).has("messageId")).isFalse();
        assertThat(request.getValue().taskType()).isEqualTo(TaskType.CONTEXT_RESOLUTION);
        assertThat(request.getValue().promptVersion()).isEqualTo("context-v32-sources");
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

    @ParameterizedTest
    @ValueSource(strings = {"CONNECTION_FAILED", "TIMEOUT", "MODEL_ERROR", "STREAM_INTERRUPTED"})
    void modelOutageIsPassedToFailureHandlingInsteadOfAskingTheCustomer(String code) {
        var outage = new GeneralException(LlmErrorCode.valueOf(code));
        when(model.generate(any())).thenThrow(outage);
        String question = "그 서비스 해지는 어떻게 해요?";
        assertThatThrownBy(() -> resolver.resolve(command(question),
                new ChatContext(1L, 5L, null, List.of(source), question, 100))).isSameAs(outage);
    }

    @Test
    void cancelledRequestIsNotTurnedIntoClarification() {
        var cancelled = new LlmStreamCancelledException();
        when(model.generate(any())).thenThrow(cancelled);
        assertThatThrownBy(() -> resolver.resolve(command("그 서비스 해지는 어떻게 해요?"), null)).isSameAs(cancelled);
    }

    @Test
    void bothModelRequestsCarryTheirOwnSchemaAndDisableThinking() throws Exception {
        respond(model, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"));
        String question = "그 서비스 해지는 어떻게 해요?";
        resolver.resolve(command(question), new ChatContext(1L, 5L, null, List.of(source), question, 100));
        var requests = ArgumentCaptor.forClass(LlmRequest.class);
        org.mockito.Mockito.verify(model, org.mockito.Mockito.times(2)).generate(requests.capture());
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.responseSchema()).isNotNull();
            assertThat(request.think()).isFalse();
            assertThat(request.promptVersion()).hasSizeLessThanOrEqualTo(30);
        });
        assertThat(requests.getAllValues().get(0).responseSchema()).isEqualTo(ChatQuestionResolver.referenceSchema());
        assertThat(requests.getAllValues().get(1).responseSchema()).isEqualTo(ChatQuestionResolver.sourcesSchema(1));
    }

    @Test
    void referenceSchemaAllowsOnlyTheThreeJudgements() {
        var schema = ChatQuestionResolver.referenceSchema();
        assertThat(schema.get("required")).isEqualTo(List.of("reference"));
        assertThat(schema.get("additionalProperties")).isEqualTo(false);
        var reference = (Map<?, ?>) ((Map<?, ?>) schema.get("properties")).get("reference");
        assertThat(reference.get("enum")).isEqualTo(List.of("EXPLICIT", "OMITTED", "AMBIGUOUS"));
    }

    @Test
    void sourcesSchemaFixesCountRangeAndAnalysisOrder() {
        var schema = ChatQuestionResolver.sourcesSchema(2);
        var properties = (Map<?, ?>) schema.get("properties");
        assertThat(properties.keySet().stream().map(Object::toString).toList())
                .containsExactly("sources", "referenceScope", "anchorIndex");
        var array = (Map<?, ?>) properties.get("sources");
        assertThat(array.get("minItems")).isEqualTo(2);
        assertThat(array.get("maxItems")).isEqualTo(2);
        var items = (Map<?, ?>) array.get("items");
        assertThat(items.get("required")).isEqualTo(List.of("index", "targetCount", "parentIndex"));
        assertThat(items.get("additionalProperties")).isEqualTo(false);
        var source = (Map<?, ?>) items.get("properties");
        assertThat(((Map<?, ?>) source.get("index")).get("maximum")).isEqualTo(2);
        assertThat(((Map<?, ?>) source.get("parentIndex")).get("maximum")).isEqualTo(1);
    }

    @Test
    void responseWithBrokenFormatStillAsksForClarification() {
        when(model.generate(any())).thenThrow(new GeneralException(LlmErrorCode.INVALID_RESPONSE));
        assertThat(resolver.resolve(command("그 서비스 해지는 어떻게 해요?"), null).needsClarification()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"그럼 비용은 얼마나 드나요?", "이전에 문의한 건 취소할 수 있어요?", "그럼 처리 기간은 얼마나 되죠?",
            "아까 물어본 거 처리 기간은요?", "그때 요금은 얼마나 나와요?", "그럼신청방법은?", "그건 어디서 신청해요?"})
    void questionMadeOnlyOfReferencesAndAskedItemsIsTreatedAsOmittedTarget(String question) {
        assertThat(ChatQuestionResolver.isGenericFollowUp(question)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"요금 안 내면 언제 정지되나요?", "요금 너무 많이 나왔어요", "요금제 변경은 어떻게 해요?",
            "그 서비스 해지는 어떻게 해요?", "이심 신청은요?", "이전 설치 신청 방법은?", "아까 신청한 유심 재발급을 취소할 수 있나요?",
            "위약금 얼마나 나와요?", "수수료는 얼마예요?"})
    void anyBusinessWordLeavesTheTargetDecisionToTheModel(String question) {
        assertThat(ChatQuestionResolver.isGenericFollowUp(question)).isFalse();
    }

    @Test
    void genericFollowUpSkipsCurrentTargetModelAndAnalyzesOnlyPreviousSources() throws Exception {
        String question = "그럼 비용은 얼마나 드나요?";
        respond(model, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"));
        var result = resolver.resolve(command(question), new ChatContext(1L, 5L, null, List.of(source), question, 100));
        assertThat(result.sourceMessageIds()).containsExactly(501L);
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model).generate(request.capture());
        assertThat(request.getValue().promptVersion()).isEqualTo("context-v32-sources");
    }

    @Test
    void ordinalReferenceCountsTopicsInCodeEvenWhenModelChoosesRecent() throws Exception {
        var correction = customer(902L, 2, "일본이 아니라 미국입니다");
        var newer = customer(903L, 3, "유심 재발급 방법도 알아보고 있어요");
        String raw = "{\"reference\":\"OMITTED\",\"anchorIndex\":0,\"referenceScope\":\"RECENT\",\"sources\":["
                + "{\"index\":1,\"targetCount\":1,\"parentIndex\":0},"
                + "{\"index\":2,\"targetCount\":0,\"parentIndex\":1},"
                + "{\"index\":3,\"targetCount\":1,\"parentIndex\":0}]}";
        var result = resolver.validateResolution("앞서 첫 번째로 물어본 건 비용이 얼마야?", raw,
                Map.of(501L, source, 902L, correction, 903L, newer));
        assertThat(result.sourceMessageIds()).containsExactly(501L, 902L);
        assertThat(result.question()).doesNotContain(newer.content());

        var second = resolver.validateResolution("앞서 두 번째로 물어본 그건 기간이 얼마나 걸려?", raw,
                Map.of(501L, source, 902L, correction, 903L, newer));
        assertThat(second.sourceMessageIds()).containsExactly(903L);
    }

    @Test
    void ordinalBeyondTopicsOrPointingAtMultipleTargetsRequiresClarification() throws Exception {
        var multiple = customer(501L, 1, "인터넷 가입과 번호이동을 둘 다 알아보고 있어요");
        var newer = customer(900L, 2, "컬러링 가입도 알아보고 있어요");
        String raw = "{\"reference\":\"OMITTED\",\"anchorIndex\":0,\"referenceScope\":\"RECENT\",\"sources\":["
                + "{\"index\":1,\"targetCount\":2,\"parentIndex\":0},"
                + "{\"index\":2,\"targetCount\":1,\"parentIndex\":0}]}";
        assertThat(resolver.validateResolution("앞서 첫 번째로 물어본 건 비용이 얼마야?", raw,
                Map.of(501L, multiple, 900L, newer)).needsClarification()).isTrue();
        assertThat(resolver.validateResolution("앞서 네 번째로 물어본 건 비용이 얼마야?", raw,
                Map.of(501L, multiple, 900L, newer)).needsClarification()).isTrue();
    }

    // 한 발언의 업무가 여럿이면 그 개수만큼 업무 순서를 센다. 발언 순서로 세지 않는다.
    @Test
    void ordinalCountsEachTargetInsideAMessageNotEachMessage() throws Exception {
        var multiple = customer(501L, 1, "인터넷 가입과 번호이동을 알아보고 있어요");
        var roaming = customer(900L, 2, "로밍도 알아보고 있어요");
        String raw = "{\"reference\":\"OMITTED\",\"anchorIndex\":0,\"referenceScope\":\"RECENT\",\"sources\":["
                + "{\"index\":1,\"targetCount\":2,\"parentIndex\":0},"
                + "{\"index\":2,\"targetCount\":1,\"parentIndex\":0}]}";
        var sources = Map.of(501L, multiple, 900L, roaming);

        var second = resolver.validateResolution("두 번째로 물어본 건 비용이 얼마야?", raw, sources);
        assertThat(second.needsClarification()).isTrue();
        assertThat(second.sourceMessageIds()).isEmpty();
        var third = resolver.validateResolution("세 번째로 물어본 건 비용이 얼마야?", raw, sources);
        assertThat(third.needsClarification()).isFalse();
        assertThat(third.sourceMessageIds()).containsExactly(900L);
    }

    // 상담 대상 낱말만 있고 지시어가 없으면 규칙으로 정하지 않고 현재 대상 판정 모델을 실제로 호출한다.
    @ParameterizedTest
    @ValueSource(strings = {"요금 언제 나와요?", "요금 변경 어떻게 해요?", "요금은 얼마예요?", "취소 어떻게 해요?",
            "신청 언제까지 해요?", "변경은 어디서 해요?", "처음 요금은 언제 나와요?"})
    void topicCapableWordWithoutReferenceIsLeftToTheModel(String question) throws Exception {
        assertThat(ChatQuestionResolver.isGenericFollowUp(question)).isFalse();
        respond(model, explicit());
        var result = resolver.resolve(command(question), null);
        assertThat(result.needsClarification()).isFalse();
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model).generate(request.capture());
        assertThat(request.getValue().promptVersion()).isEqualTo("context-v32-reference");
    }

    // 허용한 앞 표시는 상담 판별용 입력에서 제외한다. 저장 원문은 변경하지 않는다.
    @Test
    void leadingTagDoesNotHideAFollowUpFromContextResolution() throws Exception {
        String question = "[문의] 비용은 얼마인가요?";
        respond(model, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"));
        var result = resolver.resolve(command(question), new ChatContext(1L, 5L, null, List.of(source), question, 100));
        assertThat(result.sourceMessageIds()).containsExactly(501L);
        assertThat(result.question()).contains(source.content(), "비용은 얼마인가요?");
    }

    @Test
    void onlyAShortLeadingBracketTagIsRemovedForGating() {
        assertThat(ChatQuestionResolver.withoutLeadingTag("[문의] 비용은 얼마인가요?")).isEqualTo("비용은 얼마인가요?");
        assertThat(ChatQuestionResolver.withoutLeadingTag("비용은 [VAT 포함] 얼마인가요?"))
                .isEqualTo("비용은 [VAT 포함] 얼마인가요?");
        assertThat(ChatQuestionResolver.withoutLeadingTag("요금 납부 방법 알려줘")).isEqualTo("요금 납부 방법 알려줘");
    }

    @ParameterizedTest
    @ValueSource(strings = {"처음 물어본 거 요금은요?", "첫 번째로 문의한 건 취소할 수 있어요?"})
    void ordinalPhraseStillCountsAsReference(String question) {
        assertThat(ChatQuestionResolver.isGenericFollowUp(question)).isTrue();
    }

    @Test
    void selfContainedFeeQuestionIsJudgedByModelAndKeepsNoHistory() throws Exception {
        String question = "요금 언제 나와요?";
        respond(model, explicit());
        var result = resolver.resolve(command(question), new ChatContext(1L, 5L, null, List.of(source), question, 100));
        assertThat(result.needsClarification()).isFalse();
        assertThat(result.sourceMessageIds()).isEmpty();
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model).generate(request.capture());
        assertThat(request.getValue().promptVersion()).isEqualTo("context-v32-reference");
    }

    // 지시어 없이 조건만 말한 짧은 후속 질문도 이전 고객 발언이 있으면 대상 생략 여부를 모델이 판정한다.
    @ParameterizedTest
    @ValueSource(strings = {"중국도 되나요?", "주말에도 접수돼요?", "자녀 명의도 괜찮아요?"})
    void shortQuestionWithCustomerHistoryIsJudgedByTheModel(String question) throws Exception {
        respond(model, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"));
        var result = resolver.resolve(command(question), new ChatContext(1L, 5L, null, List.of(source), question, 100));
        assertThat(result.sourceMessageIds()).containsExactly(501L);
        assertThat(result.question()).contains(source.content(), question);
    }

    // 이전 고객 발언이 없거나 인사뿐이면, 또는 질문이 길면 지금처럼 모델 없이 독립 질문으로 둔다.
    @Test
    void shortQuestionWithoutCustomerHistoryOrLongQuestionSkipsTheModel() throws Exception {
        String shortQuestion = "중국도 되나요?";
        var greeting = customer(502L, 1, "안녕하세요");
        assertThat(resolver.resolve(command(shortQuestion), null).sourceMessageIds()).isEmpty();
        assertThat(resolver.resolve(command(shortQuestion),
                new ChatContext(1L, 5L, null, List.of(greeting), shortQuestion, 100)).sourceMessageIds()).isEmpty();
        String longQuestion = "중국 출장 가는데 현지에서 데이터 무제한으로 쓰려면 어떻게 해야 하나요?";
        assertThat(resolver.resolve(command(longQuestion),
                new ChatContext(1L, 5L, null, List.of(source), longQuestion, 100)).sourceMessageIds()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(model);
    }

    // 흔들림 보완을 켜면 비용 항목만 묻는 후속 질문은 길어도 이전 고객 발언이 있을 때 모델이 판정한다.
    // 이전 발언이 없거나 보완을 끄면 지금처럼 모델 없이 독립 질문이다.
    @Test
    void longCostItemQuestionWithCustomerHistoryIsJudgedByTheModelWhenGuarded() throws Exception {
        String question = "설치 기사님 방문비용은 따로 청구되는 건가요?";
        var context = new ChatContext(1L, 5L, null, List.of(source), question, 100);
        assertThat(guarded.resolve(command(question), null).sourceMessageIds()).isEmpty();
        assertThat(resolver.resolve(command(question), context).sourceMessageIds()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(model);
        respond(model, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"));
        assertThat(guarded.resolve(command(question), context).sourceMessageIds()).containsExactly(501L);
    }

    // 흔들림 보완을 켜면 고객 발언이 하나뿐이고 하려는 일을 말했을 때 모델이 그 발언의 업무를 0개로 세도 그 발언에 연결한다.
    // 사실만 말한 발언, 업무를 둘로 센 발언은 그대로 확인 안내이고, 보완을 끄면 지금처럼 확인 안내다.
    @Test
    void onlyCustomerStatementIsLinkedEvenWhenModelCountsNoBusinessWhenGuarded() throws Exception {
        String none = omitted("[{\"index\":1,\"targetCount\":0,\"parentIndex\":0}]");
        assertThat(guarded.validateResolution("가족이 대신 가도 되나요?", none, Map.of(501L, source))
                .sourceMessageIds()).containsExactly(501L);
        assertThat(resolver.validateResolution("가족이 대신 가도 되나요?", none, Map.of(501L, source))
                .needsClarification()).isTrue();
        assertThat(guarded.validateResolution("가족이 대신 가도 되나요?",
                omitted("[{\"index\":1,\"targetCount\":2,\"parentIndex\":0}]"), Map.of(501L, source))
                .needsClarification()).isTrue();
        // 앞 표시 [문의]의 '문의'는 하려는 일을 말한 것으로 보지 않는다
        for (String fact : List.of("회선이 세 개 있어요", "폰을 바꾼 지 한 달 됐어요", "[문의] 회선이 세 개 있어요")) {
            var statement = customer(502L, 1, fact);
            assertThat(guarded.validateResolution("그거 어떻게 해요?", none, Map.of(502L, statement))
                    .needsClarification()).isTrue();
        }
    }

    // 흔들림 보완을 켜면 이전 발언 분석 응답이 틀릴 때 한 번만 다시 묻고, 다시 틀리면 확인 안내로 끝낸다. 끄면 다시 묻지 않는다.
    @Test
    void invalidSourceAnalysisIsAskedOnceMoreWhenGuarded() {
        String question = "그건 얼마야?";
        String invalid = "{\"sources\":[{\"index\":1,\"targetCount\":1,\"parentIndex\":0},"
                + "{\"index\":3,\"targetCount\":0,\"parentIndex\":1}],\"referenceScope\":\"RECENT\",\"anchorIndex\":0}";
        String valid = "{\"sources\":[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}],\"referenceScope\":\"RECENT\",\"anchorIndex\":0}";
        var context = new ChatContext(1L, 5L, null, List.of(source), question, 100);
        when(model.generate(any())).thenReturn(invalid, valid);
        assertThat(guarded.resolve(command(question), context).sourceMessageIds()).containsExactly(501L);

        for (boolean guards : List.of(true, false)) {
            var retried = mock(LlmClient.class);
            when(retried.generate(any())).thenReturn(invalid);
            var result = new ChatQuestionResolver(retried, mapper, ExecutionTrace.noop(), Mode.REGEX_GATED, guards)
                    .resolve(command(question), context);
            assertThat(result.needsClarification()).isTrue();
            verify(retried, org.mockito.Mockito.times(guards ? 2 : 1)).generate(any());
        }
    }

    // 흔들림 보완을 켜면 '그 상품'처럼 지시어가 붙은 일반 명사도 지시어로 본다
    @Test
    void demonstrativeProductPhraseIsGenericOnlyWhenGuarded() {
        assertThat(ChatQuestionResolver.isGenericFollowUp("그 상품의 이용 기간은요?", true)).isTrue();
        assertThat(ChatQuestionResolver.isGenericFollowUp("그 상품의 이용 기간은요?")).isFalse();
    }

    // gpt-oss는 내부 추론도 출력 한도를 써서 한도가 작으면 답이 비거나 잘린다. 흔들림 보완과 관계없이 두 요청 모두 512 이상이다
    @Test
    void outputTokenLimitIsAtLeast512WithOrWithoutGuards() throws Exception {
        String question = "그 서비스 해지는 어떻게 해요?";
        var context = new ChatContext(1L, 5L, null, List.of(source), question, 100);
        respond(model, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"));
        guarded.resolve(command(question), context);
        resolver.resolve(command(question), context);
        var requests = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model, org.mockito.Mockito.times(4)).generate(requests.capture());
        var all = requests.getAllValues();
        assertThat(all).allSatisfy(request -> assertThat(request.maxTokens()).isGreaterThanOrEqualTo(512));
    }

    // 짧아도 상품이나 업무가 있으면 모델이 EXPLICIT으로 판정해 이전 주제를 붙이지 않는다.
    @Test
    void shortQuestionWithItsOwnTargetKeepsNoHistory() throws Exception {
        String question = "청소년 요금제도 있나요?";
        respond(model, explicit());
        var result = resolver.resolve(command(question), new ChatContext(1L, 5L, null, List.of(source), question, 100));
        assertThat(result.sourceMessageIds()).isEmpty();
        assertThat(result.question()).isEqualTo(question);
        verify(model).generate(any());
    }

    // 대화 속 발언을 순서로 가리키면 일반어 목록 밖의 낱말이 있고 15자를 넘어도 현재 대상 판정 없이 업무 순서대로 연결한다.
    @Test
    void ordinalQuestionBeyondShortLengthIsStillResolved() throws Exception {
        String question = "두 번째로 물어본 건 수수료가 얼마예요?";
        var newer = customer(903L, 2, "인터넷 설치를 신청하려고 합니다");
        respond(model, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0},"
                + "{\"index\":2,\"targetCount\":1,\"parentIndex\":0}]"));
        var result = resolver.resolve(command(question),
                new ChatContext(1L, 5L, null, List.of(source, newer), question, 100));
        assertThat(result.sourceMessageIds()).containsExactly(903L);
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model).generate(request.capture());
        assertThat(request.getValue().promptVersion()).isEqualTo("context-v32-sources");
    }

    // '처음 신청한'은 일반 문장에도 쓰여 규칙으로 대상 생략을 정하지 않고 현재 대상 판정 모델에 맡긴다.
    @Test
    void firstTimeApplicantPhraseIsStillJudgedByTheModel() throws Exception {
        String question = "처음 신청한 사람도 할인 받을 수 있나요?";
        respond(model, explicit());
        var result = resolver.resolve(command(question), new ChatContext(1L, 5L, null, List.of(source), question, 100));
        assertThat(result.sourceMessageIds()).isEmpty();
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model).generate(request.capture());
        assertThat(request.getValue().promptVersion()).isEqualTo("context-v32-reference");
    }

    // 앞 표시는 공통 분석 입력에서 제외하므로 모델 입력과 복원 질문에 같은 본문을 쓴다.
    @Test
    void leadingTagIsRemovedFromAnalysisInput() throws Exception {
        String question = "[문의] 기사님 방문비용은 따로 청구되나요?";
        respond(model, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"));
        var result = resolver.resolve(command(question), new ChatContext(1L, 5L, null, List.of(source), question, 100));
        var requests = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model, org.mockito.Mockito.times(2)).generate(requests.capture());
        for (LlmRequest request : requests.getAllValues()) {
            assertThat(mapper.readTree(request.userPrompt()).path("currentQuestion").asText())
                    .isEqualTo("기사님 방문비용은 따로 청구되나요?");
        }
        assertThat(result.question()).contains("기사님 방문비용은 따로 청구되나요?").doesNotContain("[문의]");
    }

    @ParameterizedTest
    @ValueSource(strings = {"[문의] 그럼 필요한 서류는 어떻게 준비하나요?",
            "[문의] 무러바라님 그럼 필요한 서류는 어떻게 준비하나요?"})
    void leadingTagAndAddressUseTheSameHistory(String question) throws Exception {
        respond(model, omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"));
        var result = resolver.resolve(command(question),
                new ChatContext(1L, 5L, null, List.of(source), question, 100));
        assertThat(result.sourceMessageIds()).containsExactly(501L);
        assertThat(result.question()).contains("그럼 필요한 서류는 어떻게 준비하나요?")
                .doesNotContain("[문의]", "무러바라님");
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
