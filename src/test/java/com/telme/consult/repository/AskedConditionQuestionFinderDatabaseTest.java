package com.telme.consult.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.telme.consult.dto.DialogueInput;
import com.telme.consult.dto.DialogueInput.LocationStatus;
import com.telme.consult.dto.DialogueInput.Purpose;
import com.telme.consult.repository.JdbcConsultStateStore.MessageLinks;
import com.telme.consult.service.DialogueService;

import org.junit.jupiter.api.Test;

import java.util.Map;

class AskedConditionQuestionFinderDatabaseTest extends LocalConsultDatabaseTest {

    @Test
    void labelsConditionWithQuestionWithoutStoredGreeting() {
        long rid = request(session);
        long question = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        var decision = new DialogueService(p -> p.fallbackText())
                .decide(new DialogueInput(rid, Purpose.NEARBY_STORE, Map.of(), Map.of(), LocationStatus.MISSING));
        states.save(session, states.load(session, rid).version(), decision, new MessageLinks(question, null, null));
        jdbc.update("UPDATE chat_messages SET content='안녕하세요! 어느 지역의 매장을 찾으시나요?' WHERE message_id=?",
                question);

        Map<String, String> labels = new AskedConditionQuestionFinder(jdbc).of(rid);

        assertEquals("어느 지역의 매장을 찾으시나요?", labels.get("location"));
    }
}
