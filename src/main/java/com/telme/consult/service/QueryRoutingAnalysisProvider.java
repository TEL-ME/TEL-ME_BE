package com.telme.consult.service;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.repository.ChatMessageRepository;
import com.telme.chat.service.ChatAnswer;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisProvider;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisResult;
import com.telme.consult.service.FollowupContextService.Context;
import com.telme.intent.dto.res.IntentRouteResponse;
import com.telme.intent.entity.QueryRouting.Intent;
import com.telme.intent.entity.QueryRouting.Method;
import com.telme.intent.service.QueryRoutingService;
import com.telme.intent.service.TooManyFaqQuestionsException;
import com.telme.intent.service.UnsupportedCompoundQuestionException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** 최초 질문은 실제 라우팅 서비스로 보내고, 대기 중 후속 답변은 기존 상담 분석 경계로 넘긴다. */
public final class QueryRoutingAnalysisProvider implements AnalysisProvider {
    private static final String UNKNOWN_GUIDANCE =
            "통신 서비스와 관련된 질문을 입력해 주세요. 요금제, 로밍, 매장 위치 등을 도와드릴 수 있습니다.";
    private static final String UNCERTAIN_GUIDANCE =
            "질문을 정확히 분류하기 어렵습니다. 궁금한 통신 서비스나 매장 정보를 조금 더 구체적으로 알려주세요.";
    private static final String COMPOUND_GUIDANCE =
            "현재 질문을 안전하게 나누어 처리하기 어려워요. 궁금한 내용을 하나씩 알려주세요.";
    private static final String TOO_MANY_FAQ_GUIDANCE =
            "FAQ 질문은 한 번에 최대 3개까지 답변할 수 있습니다. 질문을 나누어 보내주세요.";
    private final ChatMessageRepository messages;
    private final QueryRoutingService routing;
    private final FollowupAnalysisProvider followups;
    private final CompoundQuestionSuggestions compoundSuggestions;

    public QueryRoutingAnalysisProvider(
            ChatMessageRepository messages,
            QueryRoutingService routing,
            FollowupAnalysisProvider followups) {
        this(messages, routing, followups, CompoundQuestionSuggestions.none());
    }

    public QueryRoutingAnalysisProvider(
            ChatMessageRepository messages,
            QueryRoutingService routing,
            FollowupAnalysisProvider followups,
            CompoundQuestionSuggestions compoundSuggestions) {
        this.messages = Objects.requireNonNull(messages);
        this.routing = Objects.requireNonNull(routing);
        this.followups = Objects.requireNonNull(followups);
        this.compoundSuggestions = Objects.requireNonNull(compoundSuggestions);
    }

    @Override
    public AnalysisResult analyze(Context context) {
        Objects.requireNonNull(context, "context");
        // 대기 후보가 있어도 새 질문일 수 있으므로 후속 분석기가 기존 상담 연결 여부를 판단한다.
        if (!context.candidates().isEmpty()) {
            AnalysisResult followup =
                    Objects.requireNonNull(followups.analyze(context), "followupAnalysis");
            // 새 질문 판정은 호출자에게 돌려 문맥 복원 후 한 번만 재라우팅한다.
            return followup;
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
        IntentRouteResponse result;
        try {
            result = context.message().equals(context.resolvedQuestion())
                    ? routing.routeSingleConsult(message, context.routingContext())
                    : routing.routeSingleConsult(message, context.routingContext(), context.resolvedQuestion());
        } catch (UnsupportedCompoundQuestionException exception) {
            // 나눈 질문으로 버튼을 만들지 못하면 기존 고정 버튼을 쓴다
            List<String> buttons = Objects.requireNonNull(
                    compoundSuggestions.suggest(exception.parts()), "compoundSuggestions");
            return AnalysisResult.direct(new ChatAnswer(
                    ChatMessage.MessageType.ANSWER,
                    COMPOUND_GUIDANCE,
                    null,
                    buttons.isEmpty() ? List.of("요금제 알려줘", "가까운 매장 찾아줘") : buttons,
                    null));
        } catch (TooManyFaqQuestionsException exception) {
            return AnalysisResult.direct(new ChatAnswer(
                    ChatMessage.MessageType.ANSWER,
                    TOO_MANY_FAQ_GUIDANCE,
                    null,
                    List.of(),
                    null));
        }
        if (result.intent() == Intent.UNKNOWN) {
            return AnalysisResult.direct(
                    new ChatAnswer(
                            ChatMessage.MessageType.ANSWER,
                            result.method() == Method.LLM
                                    && result.confidence() != null
                                    && result.confidence().compareTo(new BigDecimal("0.5")) < 0
                                            ? UNCERTAIN_GUIDANCE : UNKNOWN_GUIDANCE,
                            ChatMessage.AnswerBasis.OUT_OF_SCOPE,
                            List.of("요금제 알려줘", "가까운 매장 찾아줘"),
                            null));
        }
        if (result.intent() == Intent.FAQ && result.subQueries() != null
                && result.subQueries().size() > 1
                && result.subQueries().stream().allMatch(
                        query -> query.intent() == ConsultRequest.Intent.FAQ)) {
            return AnalysisResult.multipleFaq(result.subQueries());
        }
        if (result.subQueries() == null || result.subQueries().size() != 1) {
            throw new IllegalStateException("단일 상담 라우팅 결과가 필요합니다.");
        }
        return new AnalysisResult(result.subQueries().getFirst(), null, LocationStatus.MISSING);
    }

    /** FAQ와 매장 찾기가 섞여 하나씩 보내 달라고 안내할 때 붙일 버튼과의 경계다. 없으면 빈 목록이다. */
    public interface CompoundQuestionSuggestions {
        List<String> suggest(List<UnsupportedCompoundQuestionException.Part> parts);

        static CompoundQuestionSuggestions none() {
            return parts -> List.of();
        }
    }

    /** 기존 consultRequestId 재사용 여부와 추출 조건을 판단하는 라우팅 모듈의 후속 답변 경계다. */
    public interface FollowupAnalysisProvider {
        AnalysisResult analyze(Context context);
    }
}
