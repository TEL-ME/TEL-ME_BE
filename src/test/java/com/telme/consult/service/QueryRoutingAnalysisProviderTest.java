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
        when(routing.route(message, routingContext))
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
        when(routing.route(message, null))
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
                .hasMessageContaining("단일 하위 질문");
    }
}
