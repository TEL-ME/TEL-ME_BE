package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.llm.dto.req.LlmRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class ChatQuestionResolutionRegressionTest {
    private final com.telme.llm.service.LlmClient model = mock(com.telme.llm.service.LlmClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ChatQuestionResolver resolver = new ChatQuestionResolver(model, mapper, ExecutionTrace.noop());

    @ParameterizedTest
    @ValueSource(strings = {"요금 안 내면 언제 정지되나요?", "요금안내면언제정지되나요?",
            "비용은 유심 재발급할 때 얼마인가요?", "비용은유심재발급할때얼마인가요?",
            "기간은 번호이동 신청 후 얼마나 걸리나요?", "필요한서류는명의변경할때무엇인가요?",
            "로밍 신청하려는데 그건 얼마예요?", "요금 납부 방법과 데이터 충전 비용 알려줘"})
    void independentCandidatesKeepTheirOriginalTextAndAreNotBlocked(String question) throws Exception {
        ChatQuestionResolverTest.respond(model, ChatQuestionResolverTest.explicit());
        var result = resolver.resolve(command(question), null);
        assertThat(result.needsClarification()).isFalse();
        assertThat(result.sourceMessageIds()).isEmpty();
        assertThat(result.question()).isEqualTo(question);
        var request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(model).generate(request.capture());
        assertThat(mapper.readTree(request.getValue().userPrompt()).path("currentQuestion").asText()).isEqualTo(question);
        assertThat(request.getValue().promptVersion()).isEqualTo("multiturn-resolution-v30-reference");
    }

    @Test
    void followupTransitionIsAnalyzedEvenWhenTheNextWordIsNotAGateKeyword() throws Exception {
        String question = "그럼 처리 기간은 얼마나 되죠?";
        var context = new ChatContext(1L, 5L, null, List.of(customer(1L, "휴대폰 해지를 신청하려고 합니다")), question, 100);
        ChatQuestionResolverTest.respond(model, ChatQuestionResolverTest.omitted("[{\"index\":1,\"targetCount\":1,\"parentIndex\":0}]"));
        var result = resolver.resolve(command(question), context);
        assertThat(result.sourceMessageIds()).containsExactly(1L);
        assertThat(result.question()).contains(context.history().getFirst().content(), question);
        org.mockito.Mockito.verify(model, org.mockito.Mockito.times(2)).generate(any());
    }

    @Test
    void unrelatedHistoryIsNotKeptForASelfContainedBillingQuestion() throws Exception {
        String question = "요금 납부 방법 알려줘";
        var context = new ChatContext(1L, 5L, null, List.of(customer(1L, "일본 로밍을 알아보고 있어요")), question, 100);
        ChatQuestionResolverTest.respond(model, ChatQuestionResolverTest.explicit());
        var result = resolver.resolve(command(question), context);
        assertThat(result.question()).isEqualTo(question);
        assertThat(resolver.contextFor(result, context)).isNull();
    }

    @Test
    void modelFailureStillReturnsClarification() {
        when(model.generate(any())).thenThrow(new IllegalStateException("모델 장애"));
        assertThat(resolver.resolve(command("요금 미납 정지는 언제 되나요?"), null).needsClarification()).isTrue();
    }

    @Test
    void contextIntegrityIsCheckedEvenOutsideTheGate() {
        var context = new ChatContext(2L, 5L, null, List.of(), "유심 재발급 방법 알려줘", 100);
        assertThat(resolver.resolve(command(context.currentQuestion()), context).needsClarification()).isTrue();
        verifyNoInteractions(model);
    }

    @Test
    void inconsistentDuplicateSourcesAreRejectedBeforeTheModelCall() {
        String question = "그건 얼마야?";
        var context = new ChatContext(1L, 5L, null, List.of(customer(1L, "일본 로밍")), question, 100,
                List.of(customer(1L, "명의 변경")));
        assertThat(resolver.resolve(command(question), context).needsClarification()).isTrue();
        verifyNoInteractions(model);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[]", "{}", "{\"relation\":\"OTHER\",\"selectedMessageIds\":[]}",
            "{\"relation\":\"HISTORY_DEPENDENT\",\"selectedMessageIds\":[1,1]}",
            "{\"relation\":\"HISTORY_DEPENDENT\",\"selectedMessageIds\":[\"1\"]}",
            "{\"relation\":\"CLARIFICATION_REQUIRED\",\"selectedMessageIds\":[1]}"})
    void invalidOutputCannotAttachAnUnverifiedTopic(String raw) {
        String question = "그건 얼마야?";
        when(model.generate(any())).thenReturn(raw);
        var context = new ChatContext(1L, 5L, null, List.of(customer(1L, "일본 로밍")), question, 100);
        assertThat(resolver.resolve(command(question), context).needsClarification()).isTrue();
    }

    private static ChatProcessingCommand command(String question) {
        return new ChatProcessingCommand(1L, 1L, 5L, question);
    }
    private static ChatContextMessage customer(long id, String content) {
        return new ChatContextMessage(id, 1, ChatMessage.Role.USER, ChatMessage.MessageType.QUESTION, content, null);
    }
}
