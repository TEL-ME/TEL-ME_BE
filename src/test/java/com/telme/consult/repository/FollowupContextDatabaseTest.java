package com.telme.consult.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.telme.chat.service.ChatActor;
import com.telme.consult.dto.DialogueDecision;
import com.telme.consult.dto.DialogueDecision.Action;
import com.telme.consult.dto.DialogueDecision.MessageOrigin;
import com.telme.consult.repository.JdbcConsultStateStore.MessageLinks;
import com.telme.consult.service.FollowupContextService;
import com.telme.global.common.exception.GeneralException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;
import java.util.UUID;

@EnabledIfEnvironmentVariable(named = "TELME_DB_TESTS", matches = "true")
class FollowupContextDatabaseTest extends LocalConsultDatabaseTest {
    private FollowupContextService contextService() {
        return new FollowupContextService(jdbc, new PendingClarificationFinder(jdbc), transaction);
    }

    @Test
    void suppliesExistingQuestionAndUserMessageWithoutChangingState() {
        long rid = request(session);
        long asked = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        states.save(
                session,
                1,
                new DialogueDecision(
                        rid, Action.ASK, Map.of(), "location", "어느 지역인가요?", MessageOrigin.TEMPLATE),
                new MessageLinks(asked, null, null));
        long replied = message(session, "USER", "QUESTION", "COMPLETED");
        jdbc.update("UPDATE chat_messages SET content='강남역이요' WHERE message_id=?", replied);
        var before = states.load(session, rid);
        var context = contextService().prepare(new ChatActor(1L, null), session, replied);
        assertEquals("강남역이요", context.message());
        assertEquals(1, context.candidates().size());
        assertEquals(rid, context.candidates().getFirst().consultRequestId());
        assertEquals(asked, context.candidates().getFirst().questionMessageId());
        assertEquals(before, states.load(session, rid));
    }

    @Test
    void noPendingQuestionStillAllowsNewQuestion() {
        long input = message(session, "USER", "QUESTION", "COMPLETED");
        assertTrue(
                contextService()
                        .prepare(new ChatActor(1L, null), session, input)
                        .candidates()
                        .isEmpty());
    }

    @Test
    void rejectsForeignOwnerAndForeignMessage() {
        long input = message(session, "USER", "QUESTION", "COMPLETED");
        assertThrows(
                GeneralException.class,
                () -> contextService().prepare(new ChatActor(2L, null), session, input));
        long otherMessage = message(session(2L, null), "USER", "QUESTION", "COMPLETED");
        assertThrows(
                GeneralException.class,
                () -> contextService().prepare(new ChatActor(1L, null), session, otherMessage));
    }

    @Test
    void rejectsClosedSession() {
        long input = message(session, "USER", "QUESTION", "COMPLETED");
        jdbc.update("UPDATE chat_sessions SET status='CLOSED' WHERE session_id=?", session);
        assertThrows(
                GeneralException.class,
                () -> contextService().prepare(new ChatActor(1L, null), session, input));
    }

    @Test
    void guestAccessStopsAfterMemberTransfer() {
        UUID guestId = UUID.randomUUID();
        long sid = session(null, guestId);
        long input = message(sid, "USER", "QUESTION", "COMPLETED");
        var actor = new ChatActor(null, guestId);
        assertEquals(input, contextService().prepare(actor, sid, input).userMessageId());
        jdbc.update("UPDATE chat_sessions SET user_id=1 WHERE session_id=?", sid);
        assertThrows(GeneralException.class, () -> contextService().prepare(actor, sid, input));
        assertEquals(
                input,
                contextService().prepare(new ChatActor(1L, null), sid, input).userMessageId());
    }
}
