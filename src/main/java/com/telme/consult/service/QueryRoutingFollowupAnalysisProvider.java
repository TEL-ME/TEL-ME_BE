package com.telme.consult.service;

import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.exception.ConsultErrorCode;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.AnalysisResult;
import com.telme.consult.service.ConsultTurnAnalysisAdapter.FollowupAnalysis;
import com.telme.consult.service.FollowupContextService.Context;
import com.telme.consult.service.QueryRoutingAnalysisProvider.FollowupAnalysisProvider;
import com.telme.global.common.exception.GeneralException;
import com.telme.intent.dto.res.FollowUpRouteResponse;
import com.telme.intent.service.QueryRoutingService;

import java.util.Objects;

/** 라우팅 모듈의 후속 분석 결과를 상담 연결 모델로 변환한다. */
public final class QueryRoutingFollowupAnalysisProvider implements FollowupAnalysisProvider {
    private final QueryRoutingService routing;

    public QueryRoutingFollowupAnalysisProvider(QueryRoutingService routing) {
        this.routing = Objects.requireNonNull(routing);
    }

    @Override
    public AnalysisResult analyze(Context context) {
        Objects.requireNonNull(context, "context");
        FollowUpRouteResponse result =
                Objects.requireNonNull(
                        routing.analyzeFollowUp(context.sessionId(), context.message()),
                        "followUpRouteResponse");
        if (!result.hasTarget()) {
            throw new GeneralException(ConsultErrorCode.STATE_CONFLICT);
        }
        if (result.disposition() == FollowUpRouteResponse.Disposition.NEW_QUESTION) {
            return AnalysisResult.rerouteRequest();
        }
        return new AnalysisResult(
                null,
                new FollowupAnalysis(
                        result.consultRequestId(), result.conditions(), result.declinedKeys()),
                locationStatus(result));
    }

    private LocationStatus locationStatus(FollowUpRouteResponse result) {
        if (result.declinedKeys().contains(FollowUpRouteResponse.LOCATION_KEY)) {
            return LocationStatus.DECLINED;
        }
        String location = result.conditions().get(FollowUpRouteResponse.LOCATION_KEY);
        return location == null || location.isBlank()
                ? LocationStatus.MISSING
                : LocationStatus.AVAILABLE;
    }
}
