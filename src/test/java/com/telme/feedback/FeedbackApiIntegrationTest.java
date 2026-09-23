package com.telme.feedback;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.feedback.repository.FeedbackStore;

/** 피드백 활성화 상태의 실제 컨텍스트로 Security → 컨트롤러 → 서비스 → DB 흐름을 검증한다. 테스트마다 롤백된다. */
@SpringBootTest(properties = "telme.feedback.enabled=true")
@AutoConfigureMockMvc
@Transactional
class FeedbackApiIntegrationTest {
    static final String URL = "/api/v1/chat/messages/{messageId}/feedback";
    static final String HISTORY_URL = "/api/v1/chat/sessions/{sessionId}/messages";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired FeedbackStore feedbackStore;

    MockHttpSession owner;
    long ownerId;
    long ownerSession;
    long answer;

    @BeforeEach
    void setUp() {
        ownerId = user("owner");
        owner = memberSession(ownerId);
        ownerSession = chatSession(ownerId, null);
        answer = message(ownerSession, "ASSISTANT", "ANSWER", "COMPLETED");
    }
    
    @Test
    void historyShowsOwnFeedbackAndRatableState() throws Exception {
        save(owner, answer, "{\"rating\":\"DISLIKE\",\"reason\":\"WRONG_INFO\",\"comment\":\"요금이 달라요\"}")
                .andExpect(status().isOk());
        message(ownerSession, "ASSISTANT", "ANSWER", "COMPLETED");
        message(ownerSession, "USER", "QUESTION", "COMPLETED");

        // sequenceNo 오름차순: [0] 평가한 답변, [1] 평가 안 한 답변, [2] 질문
        mvc.perform(get(HISTORY_URL, ownerSession).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messages[0].ratable").value(true))
                .andExpect(jsonPath("$.result.messages[0].myFeedback.rating").value("DISLIKE"))
                .andExpect(jsonPath("$.result.messages[0].myFeedback.reason").value("WRONG_INFO"))
                .andExpect(jsonPath("$.result.messages[0].myFeedback.comment").value("요금이 달라요"))
                .andExpect(jsonPath("$.result.messages[1].ratable").value(true))
                .andExpect(jsonPath("$.result.messages[1].myFeedback").value(nullValue()))
                .andExpect(jsonPath("$.result.messages[2].ratable").value(false))
                .andExpect(jsonPath("$.result.messages[2].myFeedback").value(nullValue()));
    }
    
    @Test
    void historyTreatsMemberWithLeftoverGuestIdAsMember() throws Exception {
        save(owner, answer, "{\"rating\":\"LIKE\"}").andExpect(status().isOk());
        MockHttpSession loggedInAfterGuest = memberSession(ownerId);
        loggedInAfterGuest.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, UUID.randomUUID());

        mvc.perform(get(HISTORY_URL, ownerSession).session(loggedInAfterGuest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messages[0].myFeedback.rating").value("LIKE"));
    }
    
    @Test
    void historyShowsGuestFeedbackSucceededOnLogin() throws Exception {
        UUID guestId = UUID.randomUUID();
        long guestChat = chatSession(null, guestId);
        long guestAnswer = message(guestChat, "ASSISTANT", "ANSWER", "COMPLETED");
        save(guestSession(guestId), guestAnswer, "{\"rating\":\"LIKE\"}").andExpect(status().isOk());
        // 로그인 승계: 세션과 피드백이 회원에게 넘어가고, guest_id는 이력용으로 함께 남는다
        jdbc.update("UPDATE chat_sessions SET user_id=? WHERE session_id=?", ownerId, guestChat);
        feedbackStore.succeedGuestFeedback(guestId, ownerId);

        mvc.perform(get(URL, guestAnswer).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.rating").value("LIKE"));
        mvc.perform(get(HISTORY_URL, guestChat).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messages[0].ratable").value(true))
                .andExpect(jsonPath("$.result.messages[0].myFeedback.rating").value("LIKE"));
    }

    @Test
    void memberCreatesReadsUpdatesAndCancelsOwnFeedback() throws Exception {
        mvc.perform(get(URL, answer).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").doesNotExist());

        save(owner, answer, "{\"rating\":\"LIKE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messageId").value(answer))
                .andExpect(jsonPath("$.result.rating").value("LIKE"));

        mvc.perform(get(URL, answer).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.rating").value("LIKE"));

        // 수정 시 LIKE였던 행이 DISLIKE와 사유로 바뀌고 행은 하나로 유지된다
        save(owner, answer, "{\"rating\":\"DISLIKE\",\"reason\":\"WRONG_INFO\",\"comment\":\"  요금이 달라요  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.rating").value("DISLIKE"))
                .andExpect(jsonPath("$.result.reason").value("WRONG_INFO"))
                .andExpect(jsonPath("$.result.comment").value("요금이 달라요"));
        assertFeedbackRows(answer, 1);

        // DISLIKE → LIKE 시 기존 reason·comment가 제거되고 행은 그대로 유지된다
        save(owner, answer, "{\"rating\":\"LIKE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messageId").value(answer))
                .andExpect(jsonPath("$.result.rating").value("LIKE"))
                .andExpect(jsonPath("$.result.reason").doesNotExist())
                .andExpect(jsonPath("$.result.comment").doesNotExist());
        assertFeedbackRows(answer, 1);

        mvc.perform(delete(URL, answer).session(owner)).andExpect(status().isOk());
        mvc.perform(get(URL, answer).session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").doesNotExist());
        assertFeedbackRows(answer, 0);
    }

    @Test
    void guestRatesOwnStoreRecommendation() throws Exception {
        UUID guestId = UUID.randomUUID();
        long storeResult = message(chatSession(null, guestId), "ASSISTANT", "STORE_RESULT", "COMPLETED");

        save(guestSession(guestId), storeResult, "{\"rating\":\"DISLIKE\",\"reason\":\"NOT_RELATED\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.reason").value("NOT_RELATED"));
    }

    @Test
    void otherMembersCannotReadSaveOrCancel() throws Exception {
        save(owner, answer, "{\"rating\":\"LIKE\"}").andExpect(status().isOk());
        MockHttpSession other = memberSession(user("other"));

        mvc.perform(get(URL, answer).session(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FEEDBACK404-0"));
        save(other, answer, "{\"rating\":\"DISLIKE\",\"reason\":\"WRONG_INFO\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FEEDBACK404-0"));
        mvc.perform(delete(URL, answer).session(other))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FEEDBACK404-0"));

        // 소유자의 피드백은 그대로 남는다
        mvc.perform(get(URL, answer).session(owner))
                .andExpect(jsonPath("$.result.rating").value("LIKE"));
    }

    @Test
    void membersAndGuestsCannotCrossAccess() throws Exception {
        UUID guestId = UUID.randomUUID();
        long guestAnswer = message(chatSession(null, guestId), "ASSISTANT", "ANSWER", "COMPLETED");

        // 회원 → 게스트 메시지, 다른 게스트 → 게스트 메시지, 게스트 → 회원 메시지
        expectUnavailable(get(URL, guestAnswer).session(owner));
        expectUnavailable(get(URL, guestAnswer).session(guestSession(UUID.randomUUID())));
        expectUnavailable(get(URL, answer).session(guestSession(guestId)));
    }

    @Test
    void missingMessageAndOthersUnratableMessageAreIndistinguishable() throws Exception {
        // 타인 메시지는 평가 가능 여부(409)보다 소유권(404)을 먼저 판단해 메시지 종류를 노출하지 않는다
        long othersQuestion = message(chatSession(user("other"), null), "USER", "QUESTION", "COMPLETED");

        for (long id : new long[] {Long.MAX_VALUE, othersQuestion}) {
            expectUnavailable(put(URL, id)
                    .session(owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"rating\":\"LIKE\"}"));
        }
    }

    @Test
    void userQuestionAndUnfinishedAnswerCannotBeRated() throws Exception {
        long question = message(ownerSession, "USER", "QUESTION", "COMPLETED");
        long generating = message(ownerSession, "ASSISTANT", "ANSWER", "GENERATING");
        long clarification = message(ownerSession, "ASSISTANT", "CLARIFICATION", "COMPLETED");
        long error = message(ownerSession, "ASSISTANT", "ERROR", "COMPLETED");
        long timeout = message(ownerSession, "ASSISTANT", "ANSWER", "TIMEOUT");
        long cancelled = message(ownerSession, "ASSISTANT", "STORE_RESULT", "CANCELLED");

        for (long id : new long[] {question, generating, clarification, error, timeout, cancelled}) {
            save(owner, id, "{\"rating\":\"LIKE\"}")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("FEEDBACK409-0"));
        }
    }

    @Test
    void invalidBodiesAreRejected() throws Exception {
        save(owner, answer, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));
        save(owner, answer, "{\"rating\":\"LIKE\",\"reason\":\"WRONG_INFO\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FEEDBACK400-0"));
        save(owner, answer, "{\"rating\":\"DISLIKE\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FEEDBACK400-0"));
        save(owner, answer, "{\"rating\":\"OTHER\"}").andExpect(status().isBadRequest());
        assertFeedbackRows(answer, 0);
    }

    @Test
    void requestWithoutIdentityGetsFreshGuestAndSeesNotFound() throws Exception {
        // GuestIdentityFilter가 /api/v1/chat/** 전체에 걸려, 세션이 없어도 새 게스트로 자동 식별된다.
        // 그 게스트는 이 메시지를 만든 적이 없으므로 401이 아니라 404로 응답한다.
        mvc.perform(get(URL, answer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FEEDBACK404-0"));
        mvc.perform(put(URL, answer).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":\"LIKE\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FEEDBACK404-0"));
    }

    ResultActions save(MockHttpSession session, long messageId, String body) throws Exception {
        return mvc.perform(put(URL, messageId)
                .session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    void expectUnavailable(MockHttpServletRequestBuilder request) throws Exception {
        mvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FEEDBACK404-0"));
    }

    void assertFeedbackRows(long messageId, int expected) {
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM message_feedback WHERE message_id=?", Integer.class, messageId);
        assertEquals(expected, rows);
    }

    long user(String name) {
        return jdbc.queryForObject("INSERT INTO users(name) VALUES (?) RETURNING user_id", Long.class, name);
    }

    long chatSession(Long userId, UUID guestId) {
        if (guestId != null) {
            jdbc.update("INSERT INTO guests(guest_id,expires_at) VALUES (?,now()+interval '1 day')", guestId);
        }
        return jdbc.queryForObject(
                "INSERT INTO chat_sessions(user_id,guest_id) VALUES (?,?) RETURNING session_id",
                Long.class,
                userId,
                guestId);
    }

    long message(long sessionId, String role, String type, String status) {
        return jdbc.queryForObject(
                "INSERT INTO chat_messages(session_id,sequence_no,role,message_type,status,content)"
                        + " VALUES (?,(SELECT coalesce(max(sequence_no),0)+1 FROM chat_messages WHERE"
                        + " session_id=?),?,?,?,'test') RETURNING message_id",
                Long.class,
                sessionId,
                sessionId,
                role,
                type,
                status);
    }

    MockHttpSession memberSession(long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        return session;
    }

    MockHttpSession guestSession(UUID guestId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guestId);
        return session;
    }
}