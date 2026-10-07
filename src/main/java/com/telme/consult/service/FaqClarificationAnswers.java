package com.telme.consult.service;

import com.telme.consult.converter.FollowupConditionConverter.Resolution;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.intent.dto.res.FollowUpRouteResponse;

import java.util.HashMap;
import java.util.Set;

/**
 * 라우팅은 매장 조건(location, serviceType)만 뽑는다. FAQ 되묻기로 물은 조건은 여기서 채운다.
 * 되묻기에 실어 보낸 선택지와 답이 똑같을 때만 채운다. 선택지를 눌러 보낸 답이 그 경우다.
 */
public final class FaqClarificationAnswers {

    private static final Set<String> ROUTED_KEYS =
            Set.of(FollowUpRouteResponse.LOCATION_KEY, FollowUpRouteResponse.SERVICE_TYPE_KEY);

    private FaqClarificationAnswers() {}

    public static Resolution fill(Resolution resolution, String reply) {
        if (resolution == null || resolution.answersWaitingField()) {
            return resolution;
        }
        var candidate = resolution.candidate();
        String answer = reply == null ? "" : reply.strip();
        if (ROUTED_KEYS.contains(candidate.field())
                || candidate.options().stream().noneMatch(answer::equals)) {
            return resolution;
        }
        var updates = new HashMap<>(resolution.updates());
        updates.put(candidate.field(), Condition.filled(answer));
        return new Resolution(candidate, updates);
    }
}
