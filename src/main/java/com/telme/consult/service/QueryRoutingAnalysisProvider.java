package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisProvider;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisResult;
import com.telme.consult.service.FollowupContextService.Context;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.service.QueryRoutingService;

import java.util.Objects;

/** 최초 질문은 실제 라우팅 서비스로 보내고, 대기 중 후속 답변은 기존 상담 분석 경계로 넘긴다. */
public final class QueryRoutingAnalysisProvider implements AnalysisProvider {
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
            return Objects.requireNonNull(followups.analyze(context), "followupAnalysis");
        }

        ChatMessage message =
                messages.findByIdWithSession(context.userMessageId())
                        .filter(value -> value.getSession().getSessionId() == context.sessionId())
                        .filter(value -> value.getRole() == ChatMessage.Role.USER)
                        .filter(value -> value.getMessageType() == ChatMessage.MessageType.QUESTION)
                        .orElseThrow(() -> new IllegalArgumentException("라우팅할 사용자 메시지가 없습니다."));
        IntentRouteResponse result = routing.route(message, context.routingContext());
        if (result.subQueries() == null || result.subQueries().size() != 1) {
            // 복합 질문을 일부만 처리하면 나머지 상담이 유실되므로 연결 전까지 명시적으로 막는다.
            throw new IllegalStateException("현재 Chat 상담 연결은 단일 하위 질문만 지원합니다.");
        }
        return new AnalysisResult(result.subQueries().getFirst(), null, LocationStatus.MISSING);
    }

    /** 기존 consultRequestId 재사용 여부와 추출 조건을 판단하는 라우팅 모듈의 후속 답변 경계다. */
    public interface FollowupAnalysisProvider {
        AnalysisResult analyze(Context context);
    }
}
