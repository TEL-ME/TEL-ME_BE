package com.telme.consult.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueDecision.MessageOrigin;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConsultServicePreparedTurnTest {

    @Test
    void acceptsZeroAsInitialJpaVersion() {
        DialogueDecision decision =
                new DialogueDecision(
                        1L, Action.PROCEED, Map.of(), null, null, MessageOrigin.NONE);

        assertThatCode(() -> new ConsultService.PreparedTurn(1L, 0, decision))
                .doesNotThrowAnyException();
    }
}
