package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.service.ChatAnswer;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisProvider;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisResult;
import com.telme.consult.service.FollowupContextService.Context;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.entity.QueryRouting.Intent;
import com.telme.intent.service.QueryRoutingService;

import java.util.List;
import java.util.Objects;

/** 최초 질문은 실제 라우팅 서비스로 보내고, 대기 중 후속 답변은 기존 상담 분석 경계로 넘긴다. */
public final class QueryRoutingAnalysisProvider implements AnalysisProvider {
    private static final String UNKNOWN_GUIDANCE =
            "통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.";
    private final ChatMessageRepository messages;
    private final QueryRoutingService routing;
    private final FollowupAnalysisProvider followups;

    public QueryRoutingAnalysisProvider(
            ChatMessageRepository messages,
            QueryRoutingService routing,
            FollowupAnalysisProvider followups) {
        this.messages = Objects.requireNonNull(messages);
        this.routing = Objects.requireNonNull(routing);
        this.followups = Objects.requireNonNull(followups);
    }

    @Override
    public AnalysisResult analyze(Context context) {
        Objects.requireNonNull(context, "context");
        // 대기 후보가 있어도 새 질문일 수 있으므로 후속 분석기가 기존 상담 연결 여부를 판단한다.
        if (!context.candidates().isEmpty()) {
            AnalysisResult followup =
                    Objects.requireNonNull(followups.analyze(context), "followupAnalysis");
            if (!followup.reroute()) {
                return followup;
            }
        }

        return routeInitial(context);
    }

    private AnalysisResult routeInitial(Context context) {
        ChatMessage message =
                messages.findByIdWithSession(context.userMessageId())
                        .filter(value -> value.getSession().getSessionId() == context.sessionId())
                        .filter(value -> value.getRole() == ChatMessage.Role.USER)
                        .filter(value -> value.getMessageType() == ChatMessage.MessageType.QUESTION)
                        .orElseThrow(() -> new IllegalArgumentException("라우팅할 사용자 메시지가 없습니다."));
        IntentRouteResponse result =
                routing.routeSingleConsult(message, context.routingContext());
        if (result.intent() == Intent.UNKNOWN) {
            return AnalysisResult.direct(
                    new ChatAnswer(
                            ChatMessage.MessageType.ANSWER,
                            UNKNOWN_GUIDANCE,
                            ChatMessage.AnswerBasis.OUT_OF_SCOPE,
                            List.of("요금제 알려줘", "가까운 매장 찾아줘"),
                            null));
        }
        if (result.subQueries() == null || result.subQueries().size() != 1) {
            throw new IllegalStateException("단일 상담 라우팅 결과가 필요합니다.");
        }
        return new AnalysisResult(result.subQueries().getFirst(), null, LocationStatus.MISSING);
    }

    /** 기존 consultRequestId 재사용 여부와 추출 조건을 판단하는 라우팅 모듈의 후속 답변 경계다. */
    public interface FollowupAnalysisProvider {
        AnalysisResult analyze(Context context);
    }
}
