package com.telme.consult.service;

import com.telme.chat.service.ChatProcessingCommand;
import com.telme.consult.converter.FollowupConditionConverter;
import com.telme.consult.converter.FollowupConditionConverter.Resolution;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.ConsultChatProcessingService.AnalyzedTurn;
import com.telme.consult.service.ConsultChatProcessingService.TurnAnalyzer;
import com.telme.consult.service.FollowupContextService.Context;
import com.telme.consult.service.FollowupSelectionValidator.Selection;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 최초 분석과 후속 답변을 구분해 상담 판단으로 넘긴다. 분석 호출은 저장 트랜잭션 밖에서 한다. */
public final class ConsultTurnAnalysisAdapter implements TurnAnalyzer {
    private final ContextProvider contexts;
    private final AnalysisProvider analysis;
    private final ConsultTurnPreparationService preparation;
    private final FollowupConditionConverter followupConverter;

    public ConsultTurnAnalysisAdapter(
            ContextProvider contexts,
            AnalysisProvider analysis,
            ConsultTurnPreparationService preparation,
            FollowupConditionConverter followupConverter) {
        this.contexts = Objects.requireNonNull(contexts);
        this.analysis = Objects.requireNonNull(analysis);
        this.preparation = Objects.requireNonNull(preparation);
        this.followupConverter = Objects.requireNonNull(followupConverter);
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
        AnalysisResult result = Objects.requireNonNull(analysis.analyze(context), "analysisResult");
        if (result.followup() != null) {
            Resolution resolution =
                    followupConverter.resolve(
                            context,
                            result.followup().consultRequestId(),
                            result.followup().extractedConditions(),
                            result.followup().declinedKeys());
            if (!resolution.answersWaitingField()) {
                var correction =
                        preparation.prepareWaitingUpdate(
                                context, resolution, result.locationStatus());
                return new AnalyzedTurn(
                        correction.preparation(),
                        null,
                        correction.purpose(),
                        correction.originalUserQuery(),
                        correction.searchQuery());
            }
            Selection selection = resolution.toSelection();
            var followup =
                    preparation.prepareFollowup(
                            context, selection, result.locationStatus());
            return new AnalyzedTurn(
                    followup.preparation(),
                    followup.followup().answeredField(),
                    followup.purpose(),
                    followup.originalUserQuery(),
                    followup.searchQuery());
        }
        return new AnalyzedTurn(
                preparation.prepareAnalysis(
                        context.sessionId(), result.initialQuery(), result.locationStatus()),
                null,
                purpose(result.initialQuery().intent().name()),
                context.message(),
                result.initialQuery().queryText());
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
            LocationStatus locationStatus) {
        public AnalysisResult {
            if ((initialQuery == null) == (followup == null)) {
                throw new IllegalArgumentException("최초 질문 또는 후속 답변 하나가 필요합니다.");
            }
            Objects.requireNonNull(locationStatus, "locationStatus");
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
