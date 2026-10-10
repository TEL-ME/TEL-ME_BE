package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.service.ChatContext;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.repository.PendingClarificationFinder.Candidate;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisResult;
import com.telme.consult.service.FollowupContextService.Context;
import com.telme.consult.service.QueryRoutingAnalysisProvider.FollowupAnalysisProvider;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;
import com.telme.intent.entity.QueryRouting;
import com.telme.intent.service.QueryRoutingService;
import com.telme.intent.service.TooManyFaqQuestionsException;
import com.telme.intent.service.UnsupportedCompoundQuestionException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class QueryRoutingAnalysisProviderTest {
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final QueryRoutingService routing = mock(QueryRoutingService.class);
    private final FollowupAnalysisProvider followups = mock(FollowupAnalysisProvider.class);
    private final QueryRoutingAnalysisProvider provider =
            new QueryRoutingAnalysisProvider(messages, routing, followups);

    @Test
    void initialQuestionUsesActualRoutingResult() {
        var routingContext =
                new ChatContext(3L, 7L, null, List.of(), "요금 납부 방법", 4);
        var context = new Context(3L, 7L, "요금 납부 방법", List.of(), routingContext);
        var session = ChatSession.builder().sessionId(3L).build();
        var message =
                ChatMessage.builder()
                        .messageId(7L)
                        .session(session)
                        .role(ChatMessage.Role.USER)
                        .messageType(ChatMessage.MessageType.QUESTION)
                        .build();
        var query =
                new IntentSubQueryResponse(
                        11L,
                        (short) 1,
                        ConsultRequest.Intent.FAQ,
                        "요금 납부 방법",
                        Map.of());
        when(messages.findByIdWithSession(7L)).thenReturn(Optional.of(message));
        when(routing.routeSingleConsult(message, routingContext))
                .thenReturn(
                        new IntentRouteResponse(
                                5L,
                                7L,
                                QueryRouting.Intent.FAQ,
                                "요금 납부 방법",
                                BigDecimal.ONE,
                                QueryRouting.Method.LLM,
                                Map.of(),
                                List.of(query)));

        AnalysisResult actual = provider.analyze(context);

        assertThat(actual.initialQuery()).isEqualTo(query);
        assertThat(actual.locationStatus()).isEqualTo(LocationStatus.MISSING);
        verifyNoInteractions(followups);
    }

    @Test
    void pendingConversationDelegatesWithoutCreatingAnotherConsultation() {
        var context =
                new Context(
                        3L,
                        8L,
                        "강남역이요",
                        List.of(
                                new Candidate(
                                        11L,
                                        "location",
                                        6L,
                                        "어느 지역인가요?",
                                        "가까운 매장 알려줘",
                                        "가까운 매장",
                                        "STORE")));
        var expected =
                new AnalysisResult(
                        null,
                        new ConsultTurnAnalysisAdapter.FollowupAnalysis(
                                11L, Map.of("location", "강남역")),
                        LocationStatus.MISSING);
        when(followups.analyze(context)).thenReturn(expected);

        assertThat(provider.analyze(context)).isSameAs(expected);
        verify(followups).analyze(context);
        verifyNoInteractions(messages, routing);
    }

    @Test
    void newQuestionWhileWaitingReturnsToCallerBeforeInitialRouting() {
        var context =
                new Context(
                        3L,
                        8L,
                        "5G 요금제는 얼마예요?",
                        List.of(
                                new Candidate(
                                        11L,
                                        "location",
                                        6L,
                                        "어느 지역인가요?",
                                        "가까운 매장 알려줘",
                                        "가까운 매장",
                                        "STORE")));
        when(followups.analyze(context)).thenReturn(AnalysisResult.rerouteRequest());

        AnalysisResult actual = provider.analyze(context);

        assertThat(actual.reroute()).isTrue();
        verify(followups).analyze(context);
        verifyNoInteractions(messages, routing);
    }

    @Test
    void unknownQuestionReturnsGuidanceWithoutConsultation() {
        var context = new Context(3L, 7L, "오늘 날씨 어때?", List.of());
        var message =
                ChatMessage.builder()
                        .messageId(7L)
                        .session(ChatSession.builder().sessionId(3L).build())
                        .role(ChatMessage.Role.USER)
                        .messageType(ChatMessage.MessageType.QUESTION)
                        .build();
        when(messages.findByIdWithSession(7L)).thenReturn(Optional.of(message));
        when(routing.routeSingleConsult(message, null))
                .thenReturn(
                        new IntentRouteResponse(
                                5L,
                                7L,
                                QueryRouting.Intent.UNKNOWN,
                                "",
                                BigDecimal.ONE,
                                QueryRouting.Method.LLM,
                                Map.of(),
                                List.of()));

        AnalysisResult actual = provider.analyze(context);

        assertThat(actual.directAnswer()).isNotNull();
        assertThat(actual.directAnswer().answerBasis())
                .isEqualTo(ChatMessage.AnswerBasis.OUT_OF_SCOPE);
    }

    @Test
    void compoundQuestionIsNotPartiallyProcessed() {
        var context = new Context(3L, 7L, "요금과 매장을 알려줘", List.of());
        var message =
                ChatMessage.builder()
                        .messageId(7L)
                        .session(ChatSession.builder().sessionId(3L).build())
                        .role(ChatMessage.Role.USER)
                        .messageType(ChatMessage.MessageType.QUESTION)
                        .build();
        var faq =
                new IntentSubQueryResponse(
                        11L, (short) 1, ConsultRequest.Intent.FAQ, "요금", Map.of());
        var store =
                new IntentSubQueryResponse(
                        12L, (short) 2, ConsultRequest.Intent.STORE, "매장", Map.of());
        when(messages.findByIdWithSession(7L)).thenReturn(Optional.of(message));
        when(routing.routeSingleConsult(message, null))
                .thenReturn(
                        new IntentRouteResponse(
                                5L,
                                7L,
                                QueryRouting.Intent.BOTH,
                                "요금과 매장",
                                BigDecimal.ONE,
                                QueryRouting.Method.LLM,
                                Map.of(),
                                List.of(faq, store)));

        assertThatThrownBy(() -> provider.analyze(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("단일 상담");
    }

    @Test
    void multipleFaqQuestionsKeepEverySubQuery() {
        var context = new Context(3L, 7L, "요금제와 로밍 신청 방법 알려줘", List.of());
        var message = ChatMessage.builder()
                .messageId(7L)
                .session(ChatSession.builder().sessionId(3L).build())
                .role(ChatMessage.Role.USER)
                .messageType(ChatMessage.MessageType.QUESTION)
                .build();
        var first = new IntentSubQueryResponse(
                11L, (short) 1, ConsultRequest.Intent.FAQ, "요금제 종류", Map.of());
        var second = new IntentSubQueryResponse(
                12L, (short) 2, ConsultRequest.Intent.FAQ, "로밍 신청 방법", Map.of());
        when(messages.findByIdWithSession(7L)).thenReturn(Optional.of(message));
        when(routing.routeSingleConsult(message, null)).thenReturn(new IntentRouteResponse(
                5L, 7L, QueryRouting.Intent.FAQ, "요금제와 로밍", BigDecimal.ONE,
                QueryRouting.Method.LLM, Map.of(), List.of(first, second)));

        AnalysisResult result = provider.analyze(context);

        assertThat(result.faqQueries()).containsExactly(first, second);
        assertThat(result.initialQuery()).isNull();
    }

    @Test
    void unsupportedCompoundQuestionReturnsSplitGuidance() {
        var context = new Context(3L, 7L, "요금제와 근처 매장 알려줘", List.of());
        var message = ChatMessage.builder()
                .messageId(7L)
                .session(ChatSession.builder().sessionId(3L).build())
                .role(ChatMessage.Role.USER)
                .messageType(ChatMessage.MessageType.QUESTION)
                .build();
        when(messages.findByIdWithSession(7L)).thenReturn(Optional.of(message));
        when(routing.routeSingleConsult(message, null))
                .thenThrow(new UnsupportedCompoundQuestionException());

        AnalysisResult result = provider.analyze(context);

        assertThat(result.directAnswer()).isNotNull();
        assertThat(result.directAnswer().content()).contains("하나씩 알려");
        assertThat(result.directAnswer().followUps()).containsExactly("요금제 알려줘", "가까운 매장 찾아줘");
        assertThat(result.initialQuery()).isNull();
    }

    // 라우터가 나눈 질문으로 버튼을 만들 수 있으면 고정 버튼 대신 쓴다
    @Test
    void compoundQuestionUsesButtonsFromSplitParts() {
        var parts = List.of(
                new UnsupportedCompoundQuestionException.Part(
                        com.telme.consult.entity.ConsultRequest.Intent.FAQ, "명의변경 필요 서류", Map.of()),
                new UnsupportedCompoundQuestionException.Part(
                        com.telme.consult.entity.ConsultRequest.Intent.STORE, "강남역 명의변경 매장",
                        Map.of("location", "강남역")));
        var withButtons = new QueryRoutingAnalysisProvider(messages, routing, followups,
                received -> received.equals(parts)
                        ? List.of("명의변경 시 필요한 서류를 정리해서 알려주세요.", "강남역 명의변경 가능한 매장을 알려주세요.")
                        : List.of());
        var message = ChatMessage.builder()
                .messageId(7L)
                .session(ChatSession.builder().sessionId(3L).build())
                .role(ChatMessage.Role.USER)
                .messageType(ChatMessage.MessageType.QUESTION)
                .build();
        when(messages.findByIdWithSession(7L)).thenReturn(Optional.of(message));
        when(routing.routeSingleConsult(message, null)).thenThrow(new UnsupportedCompoundQuestionException(parts));

        AnalysisResult result = withButtons.analyze(
                new Context(3L, 7L, "명의변경 서류 알려주고 강남역 근처 매장도 찾아줘", List.of()));

        assertThat(result.directAnswer().content()).contains("하나씩 알려");
        assertThat(result.directAnswer().followUps())
                .containsExactly("명의변경 시 필요한 서류를 정리해서 알려주세요.", "강남역 명의변경 가능한 매장을 알려주세요.");
    }

    @Test
    void tooManyFaqQuestionsReturnsLimitGuidance() {
        var context = new Context(3L, 7L, "요금제, 로밍, 명의변경, 유심 알려줘", List.of());
        var message = ChatMessage.builder()
                .messageId(7L)
                .session(ChatSession.builder().sessionId(3L).build())
                .role(ChatMessage.Role.USER)
                .messageType(ChatMessage.MessageType.QUESTION)
                .build();
        when(messages.findByIdWithSession(7L)).thenReturn(Optional.of(message));
        when(routing.routeSingleConsult(message, null))
                .thenThrow(new TooManyFaqQuestionsException(3));

        AnalysisResult result = provider.analyze(context);

        assertThat(result.directAnswer().content()).contains("최대 3개", "나누어 보내");
        assertThat(result.initialQuery()).isNull();
    }

    @Test
    void lowConfidenceReturnsClarificationGuidance() {
        var context = new Context(3L, 7L, "로밍 요금 알려줘", List.of());
        var message = ChatMessage.builder()
                .messageId(7L)
                .session(ChatSession.builder().sessionId(3L).build())
                .role(ChatMessage.Role.USER)
                .messageType(ChatMessage.MessageType.QUESTION)
                .build();
        when(messages.findByIdWithSession(7L)).thenReturn(Optional.of(message));
        when(routing.routeSingleConsult(message, null))
                .thenReturn(new IntentRouteResponse(
                        5L, 7L, QueryRouting.Intent.UNKNOWN, "로밍 요금 알려줘",
                        new BigDecimal("0.3"), QueryRouting.Method.LLM,
                        Map.of(), List.of()));

        AnalysisResult result = provider.analyze(context);

        assertThat(result.directAnswer().content()).contains("구체적으로");
    }
}
