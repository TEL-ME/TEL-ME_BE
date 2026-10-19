package com.telme.consult.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueDecision.MessageOrigin;
import com.telme.consult.dto.DialogueInput;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.ConditionStatus;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class DialogueServiceTest {
    private final DialogueService service = new DialogueService();

    private DialogueInput input(
            Map<String, Condition> old, Map<String, Condition> updates, LocationStatus location) {
        return new DialogueInput(10L, Purpose.NEARBY_STORE, old, updates, location);
    }

    @Test
    void actualCoordinatesProceedAndResolvePendingLocationWithoutInventingRegion() {
        var result = service.decide(input(
                Map.of("location", Condition.pending(), "serviceType", Condition.declined()),
                Map.of(), LocationStatus.COORDINATES_AVAILABLE));
        assertEquals(Action.PROCEED, result.action());
        assertEquals(ConditionStatus.COORDINATES, result.conditions().get("location").status());
        assertEquals(null, result.conditions().get("location").value());
    }

    @Test
    void clarificationFollowupAndCorrectionResumeSameConsultation() {
        var flow = new DialogueService();
        var first =
                flow.decide(
                        input(
                                Map.of("serviceType", Condition.filled("유심교체")),
                                Map.of(),
                                LocationStatus.MISSING));
        assertEquals(Action.ASK, first.action());
        assertEquals("location", first.waitingField());

        // 자연어 분석은 2-1팀 책임: 추출된 후속 조건을 목업으로 전달한다.
        var resumed =
                flow.decide(
                        input(
                                first.conditions(),
                                Map.of("location", Condition.filled("강남역")),
                                LocationStatus.MISSING));
        assertEquals(Action.PROCEED, resumed.action());
        assertEquals(first.consultRequestId(), resumed.consultRequestId());
        assertEquals("유심교체", resumed.conditions().get("serviceType").value());
        assertNull(resumed.waitingField());

        var corrected =
                flow.decide(
                        input(
                                resumed.conditions(),
                                Map.of("location", Condition.filled("홍대입구역")),
                                LocationStatus.MISSING));
        assertEquals(Action.PROCEED, corrected.action());
        assertEquals("홍대입구역", corrected.conditions().get("location").value());
        assertEquals("강남역", resumed.conditions().get("location").value());
    }

    @Test
    void missingRegionAsksAndKeepsRequest() {
        var result = service.decide(input(Map.of(), Map.of(), LocationStatus.MISSING));
        assertEquals(Action.ASK, result.action());
        assertEquals("location", result.waitingField());
        assertEquals(10L, result.consultRequestId());
        assertEquals(MessageOrigin.TEMPLATE, result.messageOrigin());
        assertFalse(result.conditions().containsKey("location"));
    }

    @Test
    void locationPermissionWithoutSearchRegionStillAsks() {
        var result = service.decide(input(Map.of(), Map.of(), LocationStatus.AVAILABLE));
        assertEquals(Action.ASK, result.action());
        assertEquals("location", result.waitingField());
    }

    @Test
    void locationPermissionDoesNotBypassPendingRegion() {
        var result =
                service.decide(
                        input(
                                Map.of("location", Condition.pending()),
                                Map.of(),
                                LocationStatus.AVAILABLE));
        assertEquals(Action.ASK, result.action());
        assertEquals(ConditionStatus.PENDING, result.conditions().get("location").status());
    }

    @Test
    void suppliedRegionResolvesWaitingEvenWhenLocationIsAvailable() {
        var result =
                service
                        .decide(
                                input(
                                        Map.of("location", Condition.pending(),
                                                "serviceType", Condition.declined()),
                                        Map.of("location", Condition.filled("강남역")),
                                        LocationStatus.AVAILABLE));
        assertEquals(Action.PROCEED, result.action());
        assertEquals(ConditionStatus.FILLED, result.conditions().get("location").status());
    }

    @Test
    void followupFillsRegionAndStopsWaiting() {
        var result =
                service
                        .decide(
                                input(
                                        Map.of("location", Condition.pending(),
                                                "serviceType", Condition.declined()),
                                        Map.of("location", Condition.filled(" 강남역 ")),
                                        LocationStatus.MISSING));
        assertEquals(Action.PROCEED, result.action());
        assertEquals("강남역", result.conditions().get("location").value());
        assertNull(result.waitingField());
        assertNull(result.message());
    }

    @Test
    void correctionPreservesOtherConditionsAndInput() {
        var old =
                new HashMap<>(
                        Map.of(
                                "location",
                                Condition.filled("강남역"),
                                "serviceType",
                                Condition.filled("유심")));
        var result =
                service
                        .decide(
                                input(
                                        old,
                                        Map.of("location", Condition.filled("홍대입구")),
                                        LocationStatus.MISSING));
        assertEquals("홍대입구", result.conditions().get("location").value());
        assertEquals("유심", result.conditions().get("serviceType").value());
        assertEquals("강남역", old.get("location").value());
        assertThrows(UnsupportedOperationException.class, () -> result.conditions().clear());
    }

    @Test
    void deniedGpsStillAllowsTypedRegion() {
        assertEquals(
                Action.PROCEED,
                service
                        .decide(
                                input(
                                        Map.of("serviceType", Condition.declined()),
                                        Map.of("location", Condition.filled("강남역")),
                                        LocationStatus.DECLINED))
                        .action());
    }

    @Test
    void deniedGpsAsksForRegion() {
        assertEquals(
                "location",
                service.decide(input(Map.of(), Map.of(), LocationStatus.DECLINED)).waitingField());
    }

    @Test
    void declinedRegionClearsOldValueAndDoesNotAskAgain() {
        var result =
                service
                        .decide(
                                input(
                                        Map.of("location", Condition.filled("강남역")),
                                        Map.of("location", Condition.declined()),
                                        LocationStatus.DECLINED));
        assertEquals(Action.ALTERNATIVE_GUIDANCE, result.action());
        assertNull(result.conditions().get("location").value());
        assertNull(result.waitingField());
    }

    @Test
    void declinedRegionWithCoordinatesReturnsGuidanceWithoutClarification() {
        var rejected = input(
                Map.of("location", Condition.filled("강남역")),
                Map.of("location", Condition.declined()),
                LocationStatus.COORDINATES_AVAILABLE);
        var result = service.decide(rejected);
        assertEquals(Action.ALTERNATIVE_GUIDANCE, result.action());
        assertEquals(ConditionStatus.DECLINED, result.conditions().get("location").status());
        assertNull(result.conditions().get("location").value());
        assertNull(result.waitingField());
        assertEquals(MessageOrigin.TEMPLATE, result.messageOrigin());
        assertTrue(result.message().contains("지역을 알려주실 수 있을 때"));
    }

    @Test
    void previousRegionDeclineStillBlocksCoordinatesButNewRegionCanReplaceIt() {
        var previous = Map.of("location", Condition.declined(),
                "serviceType", Condition.declined());
        assertEquals(Action.ALTERNATIVE_GUIDANCE,
                service.decide(input(previous, Map.of(), LocationStatus.COORDINATES_AVAILABLE)).action());
        var updated = service.decide(input(
                previous, Map.of("location", Condition.filled("강남역")), LocationStatus.COORDINATES_AVAILABLE));
        assertEquals(Action.PROCEED, updated.action());
        assertEquals("강남역", updated.conditions().get("location").value());
    }

    @Test
    void 지역이_정해지면_업무를_선택지와_함께_묻는다() {
        var result = service.decide(input(
                Map.of(), Map.of("location", Condition.filled("강남역")), LocationStatus.MISSING));

        assertEquals(Action.ASK, result.action());
        assertEquals("serviceType", result.waitingField());
        assertEquals("어떤 업무로 매장을 찾으시나요?", result.message());
        assertEquals(MessageOrigin.TEMPLATE, result.messageOrigin());
        assertEquals(java.util.List.of("유심 재발급", "번호이동", "신규 개통", "명의변경"), result.options());
        assertEquals(ConditionStatus.PENDING, result.conditions().get("serviceType").status());
    }

    @Test
    void 지역과_업무가_모두_없으면_지역을_먼저_묻는다() {
        var result = service.decide(input(Map.of(), Map.of(), LocationStatus.MISSING));

        assertEquals("location", result.waitingField());
        assertTrue(result.options().isEmpty());
    }

    @Test
    void 좌표로_지역이_정해져도_업무는_묻는다() {
        var result = service.decide(
                input(Map.of(), Map.of(), LocationStatus.COORDINATES_AVAILABLE));

        assertEquals(Action.ASK, result.action());
        assertEquals("serviceType", result.waitingField());
    }

    @Test
    void 업무를_받으면_더_묻지_않는다() {
        var result = service.decide(input(
                Map.of("location", Condition.filled("강남역"), "serviceType", Condition.pending()),
                Map.of("serviceType", Condition.filled("유심 재발급")),
                LocationStatus.MISSING));

        assertEquals(Action.PROCEED, result.action());
        assertEquals("유심 재발급", result.conditions().get("serviceType").value());
        assertNull(result.waitingField());
    }

    @Test
    void 업무를_고르지_않겠다고_하면_업무_없이_진행한다() {
        var result = service.decide(input(
                Map.of("location", Condition.filled("강남역"), "serviceType", Condition.pending()),
                Map.of("serviceType", Condition.declined()),
                LocationStatus.MISSING));

        assertEquals(Action.PROCEED, result.action());
        assertEquals(ConditionStatus.DECLINED, result.conditions().get("serviceType").status());
    }

    @Test
    void 일반_FAQ는_업무를_묻지_않는다() {
        var faq = new DialogueInput(12L, Purpose.GENERAL_FAQ,
                Map.of(), Map.of(), LocationStatus.COORDINATES_AVAILABLE);

        assertEquals(Action.PROCEED, service.decide(faq).action());
    }

    @Test
    void regionDeclineDoesNotBlockGeneralFaq() {
        var faq = new DialogueInput(11L, Purpose.GENERAL_FAQ,
                Map.of("location", Condition.declined()), Map.of(), LocationStatus.COORDINATES_AVAILABLE);
        assertEquals(Action.PROCEED, service.decide(faq).action());
    }

    @Test
    void generalFaqDoesNotRequireLocation() {
        var input =
                new DialogueInput(
                        11L, Purpose.GENERAL_FAQ, Map.of(), Map.of(), LocationStatus.MISSING);
        assertEquals(Action.PROCEED, service.decide(input).action());
    }

    @Test
    void missingRegionAsksWithFixedQuestion() {
        for (var location : java.util.List.of(LocationStatus.MISSING, LocationStatus.DECLINED)) {
            var result = service.decide(input(Map.of(), Map.of(), location));
            assertEquals(Action.ASK, result.action());
            assertEquals(MessageOrigin.TEMPLATE, result.messageOrigin());
            assertEquals("어느 지역의 매장을 찾으시나요? 역 이름이나 동네를 알려주세요.", result.message());
        }
    }

    @Test
    void immutableInputAndInvalidConditions() {
        var old = new HashMap<String, Condition>();
        var snapshot = input(old, Map.of(), LocationStatus.MISSING);
        old.put("location", Condition.filled("부산"));
        assertTrue(snapshot.previousConditions().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Condition.filled(" "));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DialogueInput(
                                null,
                                Purpose.GENERAL_FAQ,
                                Map.of(),
                                Map.of(),
                                LocationStatus.MISSING));
    }
}
