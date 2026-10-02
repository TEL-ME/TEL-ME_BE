package com.telme.consult.service;

import com.telme.consult.converter.ConsultAnalysisConverter;
import com.telme.consult.converter.FollowupConditionConverter.Resolution;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.dto.PlanChangeConditions;
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
    // 기존 정책은 재질문 메시지 중복만 방지하고 미해결 응답 상한은 없었다.
    // 동일 대기 조건에 해석 불가 응답 2회면 값 없이 일반 기준 안내로 종료한다.
    static final int MAX_UNRESOLVED_PLAN_REPLIES = 2;
    private final ConsultAnalysisConverter analysisConverter;
    private final FollowupSelectionValidator selectionValidator;
    private final ConsultService consultService;

    public void abandonPlanWaiting(Context context) {
        context.candidates().stream().filter(candidate -> PlanChangeConditions.KEYS.contains(candidate.field()))
                .map(candidate -> candidate.consultRequestId()).distinct()
                .forEach(requestId -> consultService.abandonWaiting(context.sessionId(), requestId));
    }

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
        var updates = resolution.updates();
        if (PlanChangeConditions.KEYS.contains(candidate.field()) && updates.isEmpty()
                && !PlanChangeConditions.isDeferred(context.message())
                && consultService.unresolvedPlanReplyCount(context.sessionId(), candidate.consultRequestId(), candidate.field()) + 1
                        >= MAX_UNRESOLVED_PLAN_REPLIES) {
            // 미확인을 아니요로 채우지 않는다. 기존 DECLINED는 값 없는 조건을 표현한다.
            updates = Map.of(candidate.field(), com.telme.consult.dto.DialogueInput.Condition.declined());
        }
        var result = consultService.prepareTurn(context.sessionId(), candidate.consultRequestId(),
                purpose, updates, locationStatus);
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
            if (purpose == null
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
