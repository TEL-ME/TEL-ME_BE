package com.telme.consult.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.telme.consult.dto.DialogueInput.Condition;
import com.telme.consult.repository.PendingClarificationFinder.Candidate;
import com.telme.consult.service.FollowupContextService.Context;
import com.telme.consult.service.FollowupSelectionValidator.Selection;
import com.telme.global.common.exception.GeneralException;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FollowupSelectionValidatorTest {
    private final FollowupSelectionValidator validator = new FollowupSelectionValidator();
    private final Context context =
            new Context(
                    1L,
                    30L,
                    "강남역이요",
                    List.of(
                            new Candidate(
                                    101L,
                                    "location",
                                    20L,
                                    "어느 지역인가요?",
                                    "유심 재발급할 매장을 알려줘",
                                    "유심 매장",
                                    "STORE")));

    @Test
    void keepsExistingConsultationAndUpdatesTogether() {
        var result =
                validator.validate(
                        context,
                        new Selection(
                                101L,
                                20L,
                                "location",
                                Map.of(
                                        "location",
                                        Condition.filled("강남역"),
                                        "serviceType",
                                        Condition.filled("USIM_REISSUE"))));
        assertEquals(101L, result.consultRequestId());
        assertEquals(30L, result.userMessageId());
        assertEquals("location", result.answeredField());
        assertEquals("강남역", result.updates().get("location").value());
        assertEquals("USIM_REISSUE", result.updates().get("serviceType").value());
    }

    @Test
    void refusalIsResolvedWithoutInventingLocation() {
        var result =
                validator.validate(
                        context,
                        new Selection(
                                101L, 20L, "location", Map.of("location", Condition.declined())));
        assertEquals(Condition.declined(), result.updates().get("location"));
    }

    @Test
    void rejectsWrongConsultationQuestionOrField() {
        for (Selection selection :
                List.of(
                        new Selection(
                                102L, 20L, "location", Map.of("location", Condition.filled("강남역"))),
                        new Selection(
                                101L, 21L, "location", Map.of("location", Condition.filled("강남역"))),
                        new Selection(
                                101L,
                                20L,
                                "serviceType",
                                Map.of("serviceType", Condition.filled("USIM_REISSUE"))))) {
            assertThrows(GeneralException.class, () -> validator.validate(context, selection));
        }
    }

    @Test
    void missingOrPendingValueCannotResolveQuestion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(context, new Selection(101L, 20L, "location", Map.of())));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        validator.validate(
                                context,
                                new Selection(
                                        101L,
                                        20L,
                                        "location",
                                        Map.of("location", Condition.pending()))));
    }

    @Test
    void noCandidateDoesNotAttachNewQuestionToOldConsultation() {
        var newQuestion = new Context(1L, 31L, "요금제 알려줘", List.of());
        assertThrows(
                GeneralException.class,
                () ->
                        validator.validate(
                                newQuestion,
                                new Selection(
                                        101L,
                                        20L,
                                        "location",
                                        Map.of("location", Condition.filled("강남역")))));
    }

    @Test
    void extractedUpdatesAreSnapshot() {
        var updates = new HashMap<String, Condition>();
        updates.put("location", Condition.filled("강남역"));
        var selection = new Selection(101L, 20L, "location", updates);
        updates.put("location", Condition.filled("홍대"));
        assertEquals(
                "강남역", validator.validate(context, selection).updates().get("location").value());
    }
}
