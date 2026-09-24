package com.telme.consult.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.telme.chat.exception.ChatErrorCode;
import com.telme.chat.service.ChatProcessingCommand;
import com.telme.consult.exception.ConsultErrorCode;
import com.telme.consult.service.ChatCommandContextProvider;
import com.telme.consult.service.FollowupContextService;
import com.telme.global.common.exception.GeneralException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.UUID;

@EnabledIfEnvironmentVariable(named = "TELME_DB_TESTS", matches = "true")
class ChatCommandContextProviderDatabaseTest extends LocalConsultDatabaseTest {

    @Test
    void loadsMemberAndGuestContextFromRunningExecution() {
        long memberMessage = userMessage(session, "회원 질문");
        long memberExecution = execution(session, memberMessage, "RUNNING");
        var member = provider().loadVerified(command(memberExecution, session, memberMessage, "회원 질문"));
        assertEquals(memberMessage, member.userMessageId());

        UUID guestId = UUID.randomUUID();
        long guestSession = session(null, guestId);
        long guestMessage = userMessage(guestSession, "비회원 질문");
        long guestExecution = execution(guestSession, guestMessage, "RUNNING");
        var guest =
                provider().loadVerified(
                        command(guestExecution, guestSession, guestMessage, "비회원 질문"));
        assertEquals(guestMessage, guest.userMessageId());
    }

    @Test
    void transferredGuestSessionUsesMemberOwnership() {
        UUID guestId = UUID.randomUUID();
        long transferredSession = session(null, guestId);
        jdbc.update(
                "UPDATE chat_sessions SET user_id=1 WHERE session_id=?",
                transferredSession);
        long input = userMessage(transferredSession, "로그인 후 질문");
        long execution = execution(transferredSession, input, "RUNNING");

        var context =
                provider().loadVerified(
                        command(execution, transferredSession, input, "로그인 후 질문"));

        assertEquals(input, context.userMessageId());
    }

    @Test
    void rejectsExecutionSessionAndMessageMismatch() {
        long input = userMessage(session, "원래 질문");
        long execution = execution(session, input, "RUNNING");
        long otherSession = session(2L, null);
        long otherInput = userMessage(otherSession, "다른 질문");

        assertError(
                ChatErrorCode.EXECUTION_NOT_FOUND,
                () -> provider().loadVerified(command(execution, otherSession, input, "원래 질문")));
        assertError(
                ChatErrorCode.EXECUTION_NOT_FOUND,
                () -> provider().loadVerified(command(execution, session, otherInput, "다른 질문")));
    }

    @Test
    void rejectsFinishedExecutionAndChangedContent() {
        long completedInput = userMessage(session, "완료 질문");
        long completed = execution(session, completedInput, "COMPLETED");
        assertError(
                ChatErrorCode.EXECUTION_NOT_RUNNING,
                () ->
                        provider().loadVerified(
                                command(completed, session, completedInput, "완료 질문")));

        long runningInput = userMessage(session, "저장된 질문");
        long running = execution(session, runningInput, "RUNNING");
        assertError(
                ConsultErrorCode.STATE_CONFLICT,
                () ->
                        provider().loadVerified(
                                command(running, session, runningInput, "변경된 질문")));
    }

    private ChatCommandContextProvider provider() {
        var contexts =
                new FollowupContextService(
                        jdbc, new PendingClarificationFinder(jdbc), transaction);
        return new ChatCommandContextProvider(jdbc, contexts, transaction);
    }

    private long userMessage(long sessionId, String content) {
        long message = message(sessionId, "USER", "QUESTION", "COMPLETED");
        jdbc.update("UPDATE chat_messages SET content=? WHERE message_id=?", content, message);
        return message;
    }

    private long execution(long sessionId, long inputMessageId, String status) {
        return jdbc.queryForObject(
                "INSERT INTO chat_executions(session_id,input_message_id,status)"
                        + " VALUES (?,?,?) RETURNING execution_id",
                Long.class,
                sessionId,
                inputMessageId,
                status);
    }

    private ChatProcessingCommand command(
            long executionId, long sessionId, long inputMessageId, String content) {
        return new ChatProcessingCommand(executionId, sessionId, inputMessageId, content);
    }

    private void assertError(
            com.telme.global.common.code.BaseErrorCode expected, Runnable operation) {
        var error = assertThrows(GeneralException.class, operation::run);
        assertEquals(expected, error.getErrorCode());
    }
}
