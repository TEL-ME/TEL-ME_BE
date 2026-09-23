package com.telme.consult.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueDecision.MessageOrigin;
import com.telme.consult.repository.JdbcConsultStateStore;
import com.telme.consult.repository.JdbcConsultStateStore.Snapshot;
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

    @Test
    void completeUsesCurrentVersionOfRequest() {
        var stateStore = mock(JdbcConsultStateStore.class);
        var service = new ConsultService(stateStore, mock(DialogueService.class));
        var current = new Snapshot(10L, 1L, 3, "PENDING", Map.of());
        var done = new Snapshot(10L, 1L, 4, "DONE", Map.of());
        when(stateStore.load(1L, 10L)).thenReturn(current);
        when(stateStore.complete(1L, 10L, 3, 99L)).thenReturn(done);

        assertEquals(done, service.complete(1L, 10L, 99L));
        verify(stateStore).complete(1L, 10L, 3, 99L);
    }
}
