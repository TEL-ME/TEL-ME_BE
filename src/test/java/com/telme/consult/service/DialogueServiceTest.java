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
import com.telme.consult.service.ClarificationTextGenerator.GenerationUnavailableException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

class DialogueServiceTest {
    private final DialogueService service = new DialogueService(p -> "어느 지역에서 매장을 찾으세요?");

    private DialogueInput input(
            Map<String, Condition> old, Map<String, Condition> updates, LocationStatus location) {
        return new DialogueInput(10L, Purpose.NEARBY_STORE, old, updates, location);
    }

    private DialogueService noModel() {
        return new DialogueService(
                p -> {
                    throw new AssertionError("모델을 호출하면 안 됨");
                });
    }

    @Test
    void clarificationFollowupAndCorrectionResumeSameConsultation() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var flow =
                new DialogueService(
                        prompt -> {
                            calls.incrementAndGet();
                            return "어느 지역에서 매장을 찾으세요?";
                        });
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
        assertEquals(1, calls.get(), "조건 충족 후 불필요한 되묻기 모델 호출을 하지 않는다");
    }

    @Test
    void missingRegionAsksAndKeepsRequest() {
        var result = service.decide(input(Map.of(), Map.of(), LocationStatus.MISSING));
        assertEquals(Action.ASK, result.action());
        assertEquals("location", result.waitingField());
        assertEquals(10L, result.consultRequestId());
        assertEquals(MessageOrigin.MODEL, result.messageOrigin());
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
                service.assess(
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
                noModel()
                        .decide(
                                input(
                                        Map.of("location", Condition.pending()),
                                        Map.of("location", Condition.filled("강남역")),
                                        LocationStatus.AVAILABLE));
        assertEquals(Action.PROCEED, result.action());
        assertEquals(ConditionStatus.FILLED, result.conditions().get("location").status());
    }

    @Test
    void followupFillsRegionAndStopsWaiting() {
        var result =
                noModel()
                        .decide(
                                input(
                                        Map.of("location", Condition.pending()),
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
                noModel()
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
                noModel()
                        .decide(
                                input(
                                        Map.of(),
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
                noModel()
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
    void generalFaqDoesNotRequireLocation() {
        var input =
                new DialogueInput(
                        11L, Purpose.GENERAL_FAQ, Map.of(), Map.of(), LocationStatus.MISSING);
        assertEquals(Action.PROCEED, noModel().decide(input).action());
    }

    @Test
    void modelFailureUsesTemplateButKeepsWaiting() {
        var failing =
                new DialogueService(
                        p -> {
                            throw new GenerationUnavailableException("timeout");
                        });
        var result = failing.decide(input(Map.of(), Map.of(), LocationStatus.MISSING));
        assertEquals(Action.ASK, result.action());
        assertEquals(MessageOrigin.TEMPLATE, result.messageOrigin());
        assertTrue(result.message().contains("역 이름"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void invalidModelTextUsesTemplate(String text) {
        var result =
                new DialogueService(p -> text)
                        .decide(input(Map.of(), Map.of(), LocationStatus.MISSING));
        assertEquals(MessageOrigin.TEMPLATE, result.messageOrigin());
    }

    @Test
    void tooLongTextUsesTemplate() {
        var result =
                new DialogueService(p -> "가".repeat(241))
                        .decide(input(Map.of(), Map.of(), LocationStatus.MISSING));
        assertEquals(MessageOrigin.TEMPLATE, result.messageOrigin());
    }

    @Test
    void programmingErrorIsNotSilentlyHidden() {
        var failing =
                new DialogueService(
                        p -> {
                            throw new IllegalStateException("bug");
                        });
        assertThrows(
                IllegalStateException.class,
                () -> failing.decide(input(Map.of(), Map.of(), LocationStatus.MISSING)));
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

    @Test
    void promptOnlyRequestsMissingRegion() {
        var inspecting =
                new DialogueService(
                        p -> {
                            assertTrue(p.userPrompt().contains("검색 지역"));
                            assertTrue(p.systemPrompt().contains("재고"));
                            assertFalse(p.userPrompt().contains("전화번호"));
                            return p.fallbackText();
                        });
        assertEquals(
                MessageOrigin.MODEL,
                inspecting
                        .decide(input(Map.of(), Map.of(), LocationStatus.MISSING))
                        .messageOrigin());
    }
}
