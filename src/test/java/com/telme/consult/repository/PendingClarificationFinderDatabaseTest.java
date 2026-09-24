package com.telme.consult.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.telme.consult.dto.DialogueInput.*;
import com.telme.consult.repository.JdbcConsultStateStore.MessageLinks;
import com.telme.consult.service.DialogueService;

import org.junit.jupiter.api.Test;

import java.util.Map;

class PendingClarificationFinderDatabaseTest extends LocalConsultDatabaseTest {
    private long ask(long rid) {
        long question = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        var decision =
                new DialogueService(p -> p.fallbackText())
                        .decide(
                                new com.telme.consult.dto.DialogueInput(
                                        rid,
                                        Purpose.NEARBY_STORE,
                                        Map.of(),
                                        Map.of(),
                                        LocationStatus.MISSING));
        states.save(
                session,
                states.load(session, rid).version(),
                decision,
                new MessageLinks(question, null, null));
        return question;
    }

    @Test
    void findsExistingRequestAndQuestionBeforeUserReply() {
        long rid = request(session);
        long question = ask(rid);
        long reply = message(session, "USER", "QUESTION", "COMPLETED");
        var found = new PendingClarificationFinder(jdbc).findBefore(session, reply);
        assertEquals(1, found.size());
        assertEquals(rid, found.getFirst().consultRequestId());
        assertEquals(question, found.getFirst().questionMessageId());
        assertEquals("location", found.getFirst().field());
    }

    @Test
    void excludesAnotherSessionAndQuestionsAfterInput() {
        long rid = request(session);
        long oldInput = message(session, "USER", "QUESTION", "COMPLETED");
        ask(rid);
        var finder = new PendingClarificationFinder(jdbc);
        assertTrue(finder.findBefore(session, oldInput).isEmpty());
        long otherSession = session(2L, null);
        long otherInput = message(otherSession, "USER", "QUESTION", "COMPLETED");
        assertTrue(finder.findBefore(session, otherInput).isEmpty());
    }

    @Test
    void keepsMultipleCandidatesAndExcludesCancelledRequest() {
        long first = request(session);
        ask(first);
        long second = request(session);
        ask(second);
        long reply = message(session, "USER", "QUESTION", "COMPLETED");
        var finder = new PendingClarificationFinder(jdbc);
        assertEquals(2, finder.findBefore(session, reply).size());
        states.cancel(session, second, states.load(session, second).version());
        assertEquals(first, finder.findBefore(session, reply).getFirst().consultRequestId());
    }
}
