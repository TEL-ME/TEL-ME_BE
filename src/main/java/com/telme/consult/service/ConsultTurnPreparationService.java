package com.telme.consult.service;

import com.telme.consult.converter.ConsultAnalysisConverter;
import com.telme.consult.converter.FollowupConditionConverter.Resolution;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.FollowupContextService.Context;
import com.telme.consult.service.FollowupSelectionValidator.ResolvedFollowup;
import com.telme.consult.service.FollowupSelectionValidator.Selection;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;

import lombok.Builder;
import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "telme.consult.persistence-enabled", havingValue = "true")
public class ConsultTurnPreparationService {
    private final ConsultAnalysisConverter analysisConverter;
    private final FollowupSelectionValidator selectionValidator;
    private final ConsultService consultService;

    // 분석에서 이미 저장한 상담 ID를 사용한다. 여기서 새 상담을 만들지 않는다.
    public ConsultService.PreparationResult prepareAnalysis(
            long sessionId, IntentSubQueryResponse analysis, LocationStatus locationStatus) {
        var input = analysisConverter.toDialogueInput(analysis, Map.of(), locationStatus);
        return consultService.prepareTurn(
                sessionId,
                input.consultRequestId(),
                input.purpose(),
                input.updates(),
                input.locationStatus());
    }

    // 소유권을 확인해 얻은 문맥과 분석에서 선택한 질문이 일치해야 이어간다.
    public PreparedFollowup prepareFollowup(
            Context context, Selection selection, LocationStatus locationStatus) {
        Objects.requireNonNull(locationStatus, "locationStatus");
        ResolvedFollowup followup = selectionValidator.validate(context, selection);
        var candidate =
                context.candidates().stream()
                        .filter(
                                value ->
                                        value.consultRequestId() == followup.consultRequestId()
                                                && value.questionMessageId()
                                                        == selection.questionMessageId()
                                                && value.field().equals(followup.answeredField()))
                        .findFirst()
                        .orElseThrow();
        Purpose purpose = purpose(candidate.intent());
        var result =
                consultService.prepareTurn(
                        context.sessionId(),
                        followup.consultRequestId(),
                        purpose,
                        followup.updates(),
                        locationStatus);
        return new PreparedFollowup(
                result,
                followup,
                purpose,
                candidate.originalUserQuery(),
                candidate.queryText());
    }

    // 기존 질문에 답하지 않고 다른 조건만 정정한 경우 질문은 유지한다.
    public PreparedWaitingUpdate prepareWaitingUpdate(
            Context context, Resolution resolution, LocationStatus locationStatus) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(locationStatus, "locationStatus");
        var candidate = resolution.candidate();
        boolean belongsToContext =
                context.candidates().stream()
                        .anyMatch(
                                value ->
                                        value.consultRequestId() == candidate.consultRequestId()
                                                && value.questionMessageId()
                                                        == candidate.questionMessageId()
                                                && value.field().equals(candidate.field()));
        if (!belongsToContext || resolution.answersWaitingField()) {
            throw new IllegalArgumentException("대기 중인 다른 조건의 정정 결과가 필요합니다.");
        }
        Purpose purpose = purpose(candidate.intent());
        var result =
                consultService.prepareTurn(
                        context.sessionId(),
                        candidate.consultRequestId(),
                        purpose,
                        resolution.updates(),
                        locationStatus);
        return new PreparedWaitingUpdate(
                result, purpose, candidate.originalUserQuery(), candidate.queryText());
    }

    private Purpose purpose(String intent) {
        return switch (intent) {
            case "STORE" -> Purpose.NEARBY_STORE;
            case "FAQ" -> Purpose.GENERAL_FAQ;
            default -> throw new IllegalArgumentException("지원하지 않는 상담 의도입니다.");
        };
    }

    /** HTTP 응답이 아닌 내부 연결 결과다. 저장할 사용자 메시지와 조건도 함께 전달한다. */
    @Builder
    public record PreparedFollowup(
            ConsultService.PreparationResult preparation,
            ResolvedFollowup followup,
            Purpose purpose,
            String originalUserQuery,
            String searchQuery) {
        public PreparedFollowup {
            Objects.requireNonNull(preparation, "preparation");
            Objects.requireNonNull(followup, "followup");
            Objects.requireNonNull(purpose, "purpose");
            if (originalUserQuery == null || originalUserQuery.isBlank()) {
                throw new IllegalArgumentException("기존 상담 원문이 필요합니다.");
            }
            if (searchQuery == null || searchQuery.isBlank()) {
                throw new IllegalArgumentException("기존 상담 검색어가 필요합니다.");
            }
            originalUserQuery = originalUserQuery.strip();
            searchQuery = searchQuery.strip();
        }
    }

    /** 대기 질문을 유지하는 내부 연결 결과다. 조건 정정이 없으면 prepared는 null일 수 있다. */
    public record PreparedWaitingUpdate(
            ConsultService.PreparationResult preparation,
            Purpose purpose,
            String originalUserQuery,
            String searchQuery) {
        public PreparedWaitingUpdate {
            Objects.requireNonNull(preparation, "preparation");
            if (!preparation.waitingForReply()
                    || purpose == null
                    || originalUserQuery == null
                    || originalUserQuery.isBlank()
                    || searchQuery == null
                    || searchQuery.isBlank()) {
                throw new IllegalArgumentException("저장할 대기 중 조건 변경이 필요합니다.");
            }
            originalUserQuery = originalUserQuery.strip();
            searchQuery = searchQuery.strip();
        }
    }
}
