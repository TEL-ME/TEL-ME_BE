package com.telme.consult.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueDecision.MessageOrigin;
import com.telme.consult.service.ConsultService.PreparedTurn;

import org.junit.jupiter.api.Test;

import java.util.Map;

class ConsultServiceTest {

    private static DialogueDecision proceed() {
        return new DialogueDecision(10L, Action.PROCEED, Map.of(), null, null, MessageOrigin.NONE);
    }

    @Test
    void preparedTurnAcceptsInitialJpaVersionZero() {
        var prepared = new PreparedTurn(1L, 0, proceed());

        assertEquals(0, prepared.expectedVersion());
    }

    @Test
    void preparedTurnRejectsNegativeVersion() {
        assertThrows(IllegalArgumentException.class, () -> new PreparedTurn(1L, -1, proceed()));
    }
}
