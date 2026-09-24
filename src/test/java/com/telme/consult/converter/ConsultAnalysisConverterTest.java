package com.telme.consult.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.entity.ConsultRequest.Intent;
import com.telme.consult.service.DialogueService;
import com.telme.intent.dto.res.IntentRouteResponse.IntentSubQueryResponse;

import org.junit.jupiter.api.Test;

import java.util.Map;

class ConsultAnalysisConverterTest {
    private final ConsultAnalysisConverter converter = new ConsultAnalysisConverter();
    private final DialogueService dialogue = new DialogueService(prompt -> "어느 지역인가요?");

    @Test
    void storeWithoutLocationNeedsClarification() {
        var input =
                converter.toDialogueInput(
                        query(Intent.STORE, Map.of("serviceType", "USIM_REISSUE")),
                        Map.of(),
                        LocationStatus.MISSING);
        assertEquals(101L, input.consultRequestId());
        assertEquals(Action.ASK, dialogue.decide(input).action());
    }

    @Test
    void extractedLocationAllowsSearch() {
        var input =
                converter.toDialogueInput(
                        query(Intent.STORE, Map.of("location", " 강남역 ")),
                        Map.of(),
                        LocationStatus.MISSING);
        assertEquals(Condition.filled("강남역"), input.updates().get("location"));
        assertEquals(Action.PROCEED, dialogue.decide(input).action());
    }

    @Test
    void blankExtractionDoesNotErasePreviousLocation() {
        var input =
                converter.toDialogueInput(
                        query(Intent.STORE, Map.of("location", " ")),
                        Map.of("location", Condition.filled("강남역")),
                        LocationStatus.MISSING);
        assertTrue(input.updates().isEmpty());
        assertEquals(Action.PROCEED, dialogue.decide(input).action());
    }

    @Test
    void faqDoesNotAskForStoreLocation() {
        var input =
                converter.toDialogueInput(
                        query(Intent.FAQ, Map.of()), Map.of(), LocationStatus.MISSING);
        assertEquals(Action.PROCEED, dialogue.decide(input).action());
    }

    @Test
    void previousRefusalIsPreserved() {
        var input =
                converter.toDialogueInput(
                        query(Intent.STORE, Map.of()),
                        Map.of("location", Condition.declined()),
                        LocationStatus.MISSING);
        assertEquals(Condition.declined(), input.previousConditions().get("location"));
        assertTrue(dialogue.decide(input).action() != Action.ASK);
    }

    @Test
    void missingStoredRequestIdIsRejected() {
        var analysis = new IntentSubQueryResponse(null, (short) 1, Intent.STORE, "매장", Map.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> converter.toDialogueInput(analysis, Map.of(), LocationStatus.MISSING));
    }

    private IntentSubQueryResponse query(Intent intent, Map<String, String> conditions) {
        return new IntentSubQueryResponse(101L, (short) 1, intent, "유심 재발급", conditions);
    }
}
