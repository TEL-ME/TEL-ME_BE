package com.telme.chat.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.faq.entity.Faq;
import com.telme.member.entity.Guest;
import com.telme.member.entity.User;
import com.telme.rag.entity.MessageSource;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatMessageSourceApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    private User owner;
    private MockHttpSession ownerSession;
    private MockHttpSession otherSession;

    @BeforeEach
    void setUpActors() {
        owner = persistUser("source-owner");
        User other = persistUser("source-other");

        ownerSession = memberSession(owner.getUserId());
        otherSession = memberSession(other.getUserId());
    }

    @Test
    void returnsMessageSourcesInSearchRankOrder() throws Exception {
        ChatSession session = persistMemberChatSession(owner.getUserId());
        ChatMessage answer = persistAnswer(session, 1);
        Faq firstFaq = persistFaq("요금제 변경 안내");
        Faq secondFaq = persistFaq("요금제 변경 제한");

        persistSource(answer, secondFaq, "두 번째 근거", (short) 2, "0.8123");
        persistSource(answer, firstFaq, "첫 번째 근거", (short) 1, "0.9123");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get(
                        "/api/v1/chat/sessions/{sessionId}/messages/{messageId}/sources",
                        session.getSessionId(), answer.getMessageId())
                        .session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messageId").value(answer.getMessageId()))
                .andExpect(jsonPath("$.result.sources.length()").value(2))
                .andExpect(jsonPath("$.result.sources[0].faqId").value(firstFaq.getFaqId()))
                .andExpect(jsonPath("$.result.sources[0].title").value("첫 번째 근거"))
                .andExpect(jsonPath("$.result.sources[0].searchRank").value(1))
                .andExpect(jsonPath("$.result.sources[0].score").value(0.9123))
                .andExpect(jsonPath("$.result.sources[1].faqId").value(secondFaq.getFaqId()));
    }

    @Test
    void returnsEmptySourcesWhenMessageHasNoRecordedEvidence() throws Exception {
        ChatSession session = persistMemberChatSession(owner.getUserId());
        ChatMessage answer = persistAnswer(session, 1);

        mockMvc.perform(get(
                        "/api/v1/chat/sessions/{sessionId}/messages/{messageId}/sources",
                        session.getSessionId(), answer.getMessageId())
                        .session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.sources.length()").value(0));
    }

    @Test
    void hidesSourcesFromNonOwner() throws Exception {
        ChatSession sourceSession = persistMemberChatSession(owner.getUserId());
        ChatMessage answer = persistAnswer(sourceSession, 1);

        mockMvc.perform(get(
                        "/api/v1/chat/sessions/{sessionId}/messages/{messageId}/sources",
                        sourceSession.getSessionId(), answer.getMessageId())
                        .session(otherSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-0"));
    }

    @Test
    void rejectsMessageFromAnotherSession() throws Exception {
        ChatSession sourceSession = persistMemberChatSession(owner.getUserId());
        ChatMessage answer = persistAnswer(sourceSession, 1);
        ChatSession otherOwnedSession = persistMemberChatSession(owner.getUserId());

        mockMvc.perform(get(
                        "/api/v1/chat/sessions/{sessionId}/messages/{messageId}/sources",
                        otherOwnedSession.getSessionId(), answer.getMessageId())
                        .session(ownerSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-2"));
    }

    @Test
    void rejectsMissingMessage() throws Exception {
        ChatSession session = persistMemberChatSession(owner.getUserId());

        mockMvc.perform(get(
                        "/api/v1/chat/sessions/{sessionId}/messages/{messageId}/sources",
                        session.getSessionId(), Long.MAX_VALUE)
                        .session(ownerSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-2"));
    }

    @Test
    void guestCanReadSourcesFromOwnedSession() throws Exception {
        UUID guestId = persistGuest();
        ChatSession session = persistGuestChatSession(guestId);
        ChatMessage answer = persistAnswer(session, 1);
        Faq faq = persistFaq("게스트 조회 근거");
        persistSource(answer, faq, "게스트 조회 근거", (short) 1, "0.9000");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get(
                        "/api/v1/chat/sessions/{sessionId}/messages/{messageId}/sources",
                        session.getSessionId(), answer.getMessageId())
                        .session(guestSession(guestId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messageId").value(answer.getMessageId()))
                .andExpect(jsonPath("$.result.sources.length()").value(1))
                .andExpect(jsonPath("$.result.sources[0].faqId").value(faq.getFaqId()));
    }

    @Test
    void memberCannotReadGuestSessionSources() throws Exception {
        UUID guestId = persistGuest();
        ChatSession guestChatSession = persistGuestChatSession(guestId);
        ChatMessage answer = persistAnswer(guestChatSession, 1);

        mockMvc.perform(get(
                        "/api/v1/chat/sessions/{sessionId}/messages/{messageId}/sources",
                        guestChatSession.getSessionId(), answer.getMessageId())
                        .session(ownerSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-0"));
    }

    @Test
    void guestCannotReadMemberSessionSources() throws Exception {
        UUID guestId = persistGuest();
        ChatSession memberChatSession = persistMemberChatSession(owner.getUserId());
        ChatMessage answer = persistAnswer(memberChatSession, 1);

        mockMvc.perform(get(
                        "/api/v1/chat/sessions/{sessionId}/messages/{messageId}/sources",
                        memberChatSession.getSessionId(), answer.getMessageId())
                        .session(guestSession(guestId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-0"));
    }

    @Test
    void ordersEqualAndMissingRanksBySourceId() throws Exception {
        ChatSession session = persistMemberChatSession(owner.getUserId());
        ChatMessage answer = persistAnswer(session, 1);
        Faq firstFaq = persistFaq("첫 번째 동일 순위 근거");
        Faq secondFaq = persistFaq("두 번째 동일 순위 근거");
        Faq unrankedFaq = persistFaq("순위 없는 근거");

        persistSource(answer, firstFaq, "첫 번째 동일 순위 근거", (short) 1, "0.9000");
        persistSource(answer, secondFaq, "두 번째 동일 순위 근거", (short) 1, "0.8000");
        persistSource(answer, unrankedFaq, "순위 없는 근거", null, "0.7000");
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get(
                        "/api/v1/chat/sessions/{sessionId}/messages/{messageId}/sources",
                        session.getSessionId(), answer.getMessageId())
                        .session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.sources[0].title").value("첫 번째 동일 순위 근거"))
                .andExpect(jsonPath("$.result.sources[1].title").value("두 번째 동일 순위 근거"))
                .andExpect(jsonPath("$.result.sources[2].title").value("순위 없는 근거"));
    }

    private User persistUser(String prefix) {
        User user = User.builder()
                .email(prefix + "-" + UUID.randomUUID() + "@example.com")
                .name(prefix)
                .build();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private UUID persistGuest() {
        UUID guestId = UUID.randomUUID();
        entityManager.persist(Guest.builder()
                .guestId(guestId)
                .lastSeenAt(Instant.now())
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());
        entityManager.flush();
        return guestId;
    }

    private ChatSession persistMemberChatSession(Long userId) {
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .title("근거 조회 테스트")
                .build();
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }

    private ChatSession persistGuestChatSession(UUID guestId) {
        ChatSession session = ChatSession.builder()
                .guestId(guestId)
                .title("게스트 근거 조회 테스트")
                .build();
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }

    private ChatMessage persistAnswer(ChatSession session, int sequenceNo) {
        ChatMessage message = ChatMessage.builder()
                .session(session)
                .sequenceNo(sequenceNo)
                .role(ChatMessage.Role.ASSISTANT)
                .messageType(ChatMessage.MessageType.ANSWER)
                .content("답변")
                .status(ChatMessage.Status.COMPLETED)
                .answerBasis(ChatMessage.AnswerBasis.GROUNDED)
                .completedAt(Instant.now())
                .build();
        entityManager.persist(message);
        entityManager.flush();
        return message;
    }

    private Faq persistFaq(String question) {
        Faq faq = Faq.builder()
                .category("PLAN")
                .question(question)
                .answer("답변")
                .build();
        entityManager.persist(faq);
        entityManager.flush();
        return faq;
    }

    private void persistSource(
            ChatMessage message,
            Faq faq,
            String title,
            Short searchRank,
            String score
    ) {
        entityManager.persist(MessageSource.builder()
                .message(message)
                .faqId(faq.getFaqId())
                .titleSnapshot(title)
                .faqVersion(faq.getVersion())
                .searchRank(searchRank)
                .score(new BigDecimal(score))
                .build());
    }

    private MockHttpSession memberSession(Long userId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, userId);
        return session;
    }

    private MockHttpSession guestSession(UUID guestId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guestId);
        return session;
    }
}
