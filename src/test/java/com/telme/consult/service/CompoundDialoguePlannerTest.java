package com.telme.consult.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueInput;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.service.CompoundDialoguePlanner.Request;
import com.telme.consult.service.CompoundDialoguePlanner.Status;
import com.telme.consult.service.CompoundDialoguePlanner.Waiting;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class CompoundDialoguePlannerTest {
    private final AtomicInteger calls = new AtomicInteger();
    private final CompoundDialoguePlanner planner =
            new CompoundDialoguePlanner(
                    new DialogueService(
                            p -> {
                                calls.incrementAndGet();
                                return p.fallbackText();
                            }));

    private Request request(
            long id, Purpose purpose, Status status, Long pending, Map<String, Condition> updates) {
        return new Request(
                new DialogueInput(id, purpose, Map.of(), updates, LocationStatus.MISSING),
                status,
                pending);
    }

    private Request store(long id) {
        return request(id, Purpose.NEARBY_STORE, Status.PENDING, null, Map.of());
    }

    @Test
    void faqCanProceedWhileStoreNeedsClarification() {
        var result =
                planner.plan(
                        List.of(
                                request(1, Purpose.GENERAL_FAQ, Status.PENDING, null, Map.of()),
                                store(2)));
        assertEquals(1L, result.ready().getFirst().consultRequestId());
        assertEquals(2L, result.clarification().consultRequestId());
        assertEquals(1, calls.get());
    }

    @Test
    void asksOnlyOneOfMultipleMissingRequests() {
        var result = planner.plan(List.of(store(2), store(3)));
        assertEquals(2L, result.clarification().consultRequestId());
        assertEquals(List.of(3L), result.deferred());
        assertEquals(1, calls.get());
    }

    @Test
    void clarificationUsesInputOrderRatherThanRequestId() {
        var result = planner.plan(List.of(store(30), store(10), store(20)));
        assertEquals(30L, result.clarification().consultRequestId());
        assertEquals(List.of(10L, 20L), result.deferred());
        assertEquals(1, calls.get());
    }

    @Test
    void existingUnansweredQuestionPreventsDuplicateAndNewModelCalls() {
        var waiting = request(2, Purpose.NEARBY_STORE, Status.WAITING_CONDITION, 99L, Map.of());
        var result = planner.plan(List.of(store(3), waiting));
        assertNull(result.clarification());
        assertEquals(List.of(new Waiting(2, 99)), result.awaiting());
        assertEquals(List.of(3L), result.deferred());
        assertEquals(0, calls.get());
    }

    @Test
    void scopedFollowupResumesOnlyItsRequestAndDoesNotRepeatCompletedFaq() {
        var done = request(1, Purpose.GENERAL_FAQ, Status.DONE, null, Map.of());
        var replied =
                request(
                        2,
                        Purpose.NEARBY_STORE,
                        Status.WAITING_CONDITION,
                        99L,
                        Map.of("location", Condition.filled("강남역")));
        var result = planner.plan(List.of(done, replied, store(3)));
        assertEquals(List.of(1L), result.closed());
        assertEquals(2L, result.ready().getFirst().consultRequestId());
        assertEquals("강남역", result.ready().getFirst().conditions().get("location").value());
        assertEquals(3L, result.clarification().consultRequestId());
        assertFalse(result.clarification().conditions().containsKey("location"));
        assertTrue(result.awaiting().isEmpty());
    }

    @Test
    void refusalProducesGuidanceAndAllowsOtherRequestsToProceed() {
        var refused =
                request(
                        2,
                        Purpose.NEARBY_STORE,
                        Status.WAITING_CONDITION,
                        99L,
                        Map.of("location", Condition.declined()));
        var result =
                planner.plan(
                        List.of(
                                refused,
                                request(1, Purpose.GENERAL_FAQ, Status.PENDING, null, Map.of())));
        assertEquals(Action.ALTERNATIVE_GUIDANCE, result.guidance().getFirst().action());
        assertEquals(1, result.ready().size());
        assertNull(result.clarification());
        assertEquals(0, calls.get());
    }

    @Test
    void duplicateAndClosedUpdatesAreRejectedBeforeModelCall() {
        assertThrows(
                IllegalArgumentException.class, () -> planner.plan(List.of(store(2), store(2))));
        var invalid =
                request(
                        3,
                        Purpose.NEARBY_STORE,
                        Status.CANCELLED,
                        null,
                        Map.of("location", Condition.filled("부산")));
        assertThrows(
                IllegalArgumentException.class, () -> planner.plan(List.of(store(2), invalid)));
        assertEquals(0, calls.get());
    }

    @Test
    void modelFailureStillProducesSingleTemplateQuestion() {
        var broken =
                new CompoundDialoguePlanner(
                        new DialogueService(
                                p -> {
                                    throw new ClarificationTextGenerator
                                            .GenerationUnavailableException("timeout");
                                }));
        var result = broken.plan(List.of(store(1), store(2)));
        assertEquals(Action.ASK, result.clarification().action());
        assertEquals(List.of(2L), result.deferred());
    }

    @Test
    void emptyPlanIsImmutableAndPendingReferenceIsValidated() {
        var result = planner.plan(List.of());
        assertTrue(result.ready().isEmpty());
        assertNull(result.clarification());
        assertThrows(UnsupportedOperationException.class, () -> result.closed().add(1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> request(1, Purpose.NEARBY_STORE, Status.PENDING, 2L, Map.of()));
    }
}
