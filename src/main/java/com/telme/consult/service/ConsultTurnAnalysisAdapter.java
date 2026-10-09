package com.telme.consult.service;

import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.chat.service.ChatQuestionResolver;
import com.telme.chat.entity.ChatMessage;
import com.telme.consult.converter.FollowupConditionConverter;
import com.telme.consult.converter.FollowupConditionConverter.Resolution;
import com.telme.consult.dto.ClarificationReask;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.entity.ConsultRequest;
import com.telme.consult.service.ConsultChatProcessingService.AnalyzedTurn;
import com.telme.consult.service.ConsultChatProcessingService.TurnAnalyzer;
import com.telme.consult.service.FollowupContextService.Context;
import com.telme.consult.service.FollowupSelectionValidator.Selection;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 최초 분석과 후속 답변을 구분해 상담 판단으로 넘긴다. 분석 호출은 저장 트랜잭션 밖에서 한다. */
public final class ConsultTurnAnalysisAdapter implements TurnAnalyzer {
    private final ContextProvider contexts;
    private final AnalysisProvider analysis;
    private final ConsultTurnPreparationService preparation;
    private final FollowupConditionConverter followupConverter;
    private final ChatQuestionResolver resolver;

    public ConsultTurnAnalysisAdapter(
            ContextProvider contexts,
            AnalysisProvider analysis,
            ConsultTurnPreparationService preparation,
            FollowupConditionConverter followupConverter) {
        this(contexts, analysis, preparation, followupConverter, null);
    }

    public ConsultTurnAnalysisAdapter(ContextProvider contexts, AnalysisProvider analysis,
            ConsultTurnPreparationService preparation, FollowupConditionConverter followupConverter,
            ChatQuestionResolver resolver) {
        this.contexts = Objects.requireNonNull(contexts);
        this.analysis = Objects.requireNonNull(analysis);
        this.preparation = Objects.requireNonNull(preparation);
        this.followupConverter = Objects.requireNonNull(followupConverter);
        this.resolver = resolver;
    }

    @Override
    public AnalyzedTurn analyze(ChatProcessingCommand command) {
        Objects.requireNonNull(command, "command");
        Context context = Objects.requireNonNull(contexts.loadVerified(command), "context");
        if (command.sessionId() == null
                || command.inputMessageId() == null
                || context.sessionId() != command.sessionId()
                || context.userMessageId() != command.inputMessageId()) {
            throw new IllegalArgumentException("분석할 사용자 메시지가 일치하지 않습니다.");
        }
        if (context.candidates().isEmpty()) {
            context = resolveQuestion(command, context);
            if (context == null) {
                return clarification();
            }
        }
        AnalysisResult result = Objects.requireNonNull(analysis.analyze(context), "analysisResult");
        Set<Long> abandoned = Set.of();
        if (result.reroute()) {
            if (context.candidates().isEmpty()) {
                throw new IllegalStateException("대기 중 상담이 없는 질문은 재라우팅할 수 없습니다.");
            }
            abandoned = context.candidates().stream()
                    .map(candidate -> candidate.consultRequestId())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            context = resolveQuestion(command, context.forNewQuestion());
            if (context == null) {
                return clarification();
            }
            result = Objects.requireNonNull(analysis.analyze(context), "reroutedAnalysisResult");
            if (result.reroute()) {
                throw new IllegalStateException("새 질문을 반복해서 재라우팅할 수 없습니다.");
            }
            // 새 질문으로 넘어간 턴의 앞선 대기 상담 정리
            preparation.cancelWaiting(context.sessionId(), abandoned);
        }
        if (result.directAnswer() != null) {
            return AnalyzedTurn.direct(result.directAnswer());
        }
        if (result.faqQueries() != null) {
            long sessionId = context.sessionId();
            List<ConsultChatProcessingService.FaqTurn> faqTurns = result.faqQueries().stream()
                    .map(query -> new ConsultChatProcessingService.FaqTurn(
                            preparation.prepareAnalysis(sessionId, query, LocationStatus.MISSING),
                            query.queryText()))
                    .toList();
            return AnalyzedTurn.multipleFaq(faqTurns)
                    .withContext(context.routingContext(), context.resolvedQuestion());
        }
        LocationStatus locationStatus = command.coordinates() == null
                ? result.locationStatus() : LocationStatus.COORDINATES_AVAILABLE;
        if (result.followup() != null) {
            Resolution resolution =
                    followupConverter.resolve(
                            context,
                            result.followup().consultRequestId(),
                            result.followup().extractedConditions(),
                            result.followup().declinedKeys());
            if (command.coordinates() != null && "location".equals(resolution.candidate().field())
                    && !resolution.answersWaitingField()) {
                var updates = new HashMap<>(resolution.updates());
                updates.put("location", Condition.coordinates());
                resolution = new Resolution(resolution.candidate(), updates);
            }
            // 라우팅이 모르는 FAQ 조건은 되묻기에 실어 보낸 선택지로 채운다
            resolution = FaqClarificationAnswers.fill(resolution, context.message());
            // 재질문에도 답이 없으면 거절로 처리
            if (resolution.updates().isEmpty()
                    && resolution.candidate().reasks() >= ClarificationReask.MAX_REASKS) {
                String field = resolution.candidate().field();
                resolution = followupConverter.resolve(
                        context, resolution.candidate().consultRequestId(), Map.of(), Set.of(field));
                if ("location".equals(field)) {
                    locationStatus = LocationStatus.DECLINED;
                }
            }
            if (!resolution.answersWaitingField()) {
                var correction =
                        preparation.prepareWaitingUpdate(
                                context, resolution, locationStatus);
                return new AnalyzedTurn(
                        correction.preparation(),
                        null,
                        correction.purpose(),
                        correction.originalUserQuery(),
                        correction.searchQuery()).withContext(null, correction.originalUserQuery());
            }
            Selection selection = resolution.toSelection();
            var followup =
                    preparation.prepareFollowup(
                            context, selection, locationStatus);
            return new AnalyzedTurn(
                    followup.preparation(),
                    followup.followup().answeredField(),
                    followup.purpose(),
                    followup.originalUserQuery(),
                    followup.searchQuery()).withContext(null, followup.originalUserQuery());
        }
        return new AnalyzedTurn(
                preparation.prepareAnalysis(
                        context.sessionId(), result.initialQuery(), locationStatus),
                null,
                purpose(result.initialQuery().intent().name()),
                context.message(),
                result.initialQuery().queryText()).withContext(context.routingContext(),
                        context.message().equals(context.resolvedQuestion())
                                ? context.message() : result.initialQuery().queryText());
    }

    private Context resolveQuestion(ChatProcessingCommand command, Context context) {
        if (resolver == null) {
            return context;
        }
        var resolution = resolver.resolve(command, context.routingContext());
        if (resolution.needsClarification()) {
            return null;
        }
        return context.withResolvedQuestion(resolution.question())
                .withRoutingContext(resolver.contextFor(resolution, context.routingContext()));
    }

    private AnalyzedTurn clarification() {
        return AnalyzedTurn.direct(new ChatAnswer(ChatMessage.MessageType.ANSWER,
                "어떤 내용에 대한 질문인지 확인이 필요합니다. 상품이나 상담 주제를 조금 더 구체적으로 알려주세요.",
                null, List.of(), null));
    }

    private Purpose purpose(String intent) {
        return switch (intent) {
            case "STORE" -> Purpose.NEARBY_STORE;
            case "FAQ" -> Purpose.GENERAL_FAQ;
            default -> throw new IllegalArgumentException("지원하지 않는 상담 의도입니다.");
        };
    }

    public interface ContextProvider {
        // 실행·메시지 연결과 대화 접근 권한을 확인한 문맥만 반환한다.
        Context loadVerified(ChatProcessingCommand command);
    }

    public interface AnalysisProvider {
        AnalysisResult analyze(Context context);
    }

    /** 내부 연결 모델. 최초 질문과 후속 답변 중 하나만 전달한다. */
    public record AnalysisResult(
            IntentSubQueryResponse initialQuery,
            FollowupAnalysis followup,
            LocationStatus locationStatus,
            ChatAnswer directAnswer,
            boolean reroute,
            List<IntentSubQueryResponse> faqQueries) {
        public AnalysisResult(
                IntentSubQueryResponse initialQuery,
                FollowupAnalysis followup,
                LocationStatus locationStatus) {
            this(initialQuery, followup, locationStatus, null, false, null);
        }

        public static AnalysisResult direct(ChatAnswer answer) {
            return new AnalysisResult(null, null, null, Objects.requireNonNull(answer), false, null);
        }

        public static AnalysisResult rerouteRequest() {
            return new AnalysisResult(null, null, null, null, true, null);
        }

        public static AnalysisResult multipleFaq(List<IntentSubQueryResponse> queries) {
            return new AnalysisResult(null, null, null, null, false, queries);
        }

        public AnalysisResult {
            int selected = (initialQuery != null ? 1 : 0)
                    + (followup != null ? 1 : 0)
                    + (directAnswer != null ? 1 : 0)
                    + (faqQueries != null ? 1 : 0)
                    + (reroute ? 1 : 0);
            if (selected != 1) {
                throw new IllegalArgumentException("분석 결과 하나가 필요합니다.");
            }
            if (initialQuery != null || followup != null) {
                Objects.requireNonNull(locationStatus, "locationStatus");
            }
            if (faqQueries != null) {
                faqQueries = List.copyOf(faqQueries);
                if (faqQueries.size() < 2 || faqQueries.stream().anyMatch(
                        query -> query.intent() != ConsultRequest.Intent.FAQ)) {
                    throw new IllegalArgumentException("여러 FAQ 하위 질문이 필요합니다.");
                }
            }
        }
    }

    /** 라우팅 모듈에서 받는 후속 답변 분석 결과다. 상담 내부 Condition 타입을 노출하지 않는다. */
    public record FollowupAnalysis(
            long consultRequestId,
            Map<String, String> extractedConditions,
            Set<String> declinedKeys) {
        public FollowupAnalysis(long consultRequestId, Map<String, String> extractedConditions) {
            this(consultRequestId, extractedConditions, Set.of());
        }

        public FollowupAnalysis {
            if (consultRequestId <= 0) {
                throw new IllegalArgumentException("기존 상담 ID가 필요합니다.");
            }
            extractedConditions =
                    Map.copyOf(
                            Objects.requireNonNull(
                                    extractedConditions, "extractedConditions"));
            declinedKeys =
                    Set.copyOf(Objects.requireNonNull(declinedKeys, "declinedKeys"));
        }
    }
}
