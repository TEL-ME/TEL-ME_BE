package com.telme.consult.converter;

import com.telme.consult.dto.DialogueInput;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.intent.dto.res.FollowUpRouteResponse;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class ConsultAnalysisConverter {
    public DialogueInput toDialogueInput(
            IntentSubQueryResponse analysis,
            Map<String, Condition> previousConditions,
            LocationStatus locationStatus) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(analysis.intent(), "intent");
        Objects.requireNonNull(analysis.conditions(), "conditions");
        Map<String, Condition> updates = new HashMap<>();
        analysis.conditions()
                .forEach(
                        (key, value) -> {
                            if (key == null || key.isBlank()) {
                                throw new IllegalArgumentException("조건 이름이 필요합니다.");
                            }
                            // 빈 추출 값으로 이전에 확인한 조건을 지우지 않는다.
                            if (value == null || value.isBlank()) {
                                return;
                            }
                            // 업무를 하나로 좁힐 수 없다는 표시는 값이 아니라 되묻지 않겠다는 뜻이다
                            updates.put(key, FollowUpRouteResponse.SERVICE_TYPE_KEY.equals(key)
                                    && FollowUpRouteResponse.SERVICE_TYPE_UNDECIDED.equals(value)
                                    ? Condition.declined()
                                    : Condition.filled(value));
                        });
        Purpose purpose =
                switch (analysis.intent()) {
                    case STORE -> Purpose.NEARBY_STORE;
                    case FAQ -> Purpose.GENERAL_FAQ;
                };
        return DialogueInput.builder()
                .consultRequestId(analysis.consultRequestId())
                .purpose(purpose)
                .previousConditions(previousConditions)
                .updates(updates)
                .locationStatus(locationStatus)
                .build();
    }
}
