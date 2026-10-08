package com.telme.consult.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueDecision.MessageOrigin;
import com.telme.consult.dto.DialogueInput;
import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class DialogueDecisionValidationTest {
    @Test
    void missingConditionsIdentifiesInvalidField() {
        var error =
                assertThrows(
                        NullPointerException.class,
                        () ->
                                new DialogueDecision(
                                        1L, Action.PROCEED, null, null, null, MessageOrigin.NONE));
        assertEquals("conditions", error.getMessage());
    }

    @Test
    void missingActionCannotReachPersistence() {
        assertThrows(
                NullPointerException.class,
                () -> new DialogueDecision(1L, null, Map.of(), null, null, MessageOrigin.NONE));
    }

    @Test
    void clarificationNeedsFieldAndCannotAskResolvedCondition() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DialogueDecision(
                                1L,
                                Action.ASK,
                                Map.of(),
                                null,
                                "어느 지역인가요?",
                                MessageOrigin.TEMPLATE));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DialogueDecision(
                                1L,
                                Action.ASK,
                                Map.of("location", Condition.filled("강남역")),
                                "location",
                                "어느 지역인가요?",
                                MessageOrigin.TEMPLATE));
    }

    @Test
    void proceedCannotCarryQuestionOrWaitingField() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DialogueDecision(
                                1L,
                                Action.PROCEED,
                                Map.of(),
                                "location",
                                null,
                                MessageOrigin.NONE));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DialogueDecision(
                                1L, Action.PROCEED, Map.of(), null, "질문", MessageOrigin.MODEL));
    }

    @Test
    void optionsOnlyBelongToClarification() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DialogueDecision(
                                1L,
                                Action.ALTERNATIVE_GUIDANCE,
                                Map.of(),
                                null,
                                "안내",
                                MessageOrigin.TEMPLATE,
                                List.of("예")));
    }

    @Test
    void blankOrTooLongOptionIsRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> clarificationWithOptions(List.of(" ")));
        assertThrows(
                IllegalArgumentException.class,
                () -> clarificationWithOptions(List.of("가".repeat(256))));
    }

    @Test
    void nullOptionIsRejectedBeforeCopy() {
        var options = new java.util.ArrayList<String>();
        options.add(null);
        assertThrows(IllegalArgumentException.class, () -> clarificationWithOptions(options));
    }

    @Test
    void absentOptionsBecomeEmptyList() {
        assertEquals(
                List.of(),
                new DialogueDecision(
                                1L, Action.ASK, Map.of(), "unpaid_bill", "질문", MessageOrigin.TEMPLATE)
                        .options());
    }

    private DialogueDecision clarificationWithOptions(List<String> options) {
        return new DialogueDecision(
                1L, Action.ASK, Map.of(), "unpaid_bill", "질문", MessageOrigin.TEMPLATE, options);
    }

    @Test
    void modelMatchingFallbackStillHasModelOrigin() {
        var input =
                new DialogueInput(
                        1L, Purpose.NEARBY_STORE, Map.of(), Map.of(), LocationStatus.MISSING);
        assertEquals(
                MessageOrigin.MODEL,
                new DialogueService(p -> p.fallbackText()).decide(input).messageOrigin());
        assertEquals(
                MessageOrigin.TEMPLATE,
                new DialogueService(ClarificationTextGenerator.template())
                        .decide(input)
                        .messageOrigin());
    }
}
