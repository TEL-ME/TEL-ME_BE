package com.telme.chat.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.member.entity.Guest;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatSessionApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManager entityManager;

    private MockHttpSession ownerSession;
    private MockHttpSession otherSession;

    @BeforeEach
    void setUpActors() {
        User owner = persistUser("owner");
        User other = persistUser("other");

        ownerSession = new MockHttpSession();
        ownerSession.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, owner.getUserId());
        otherSession = new MockHttpSession();
        otherSession.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, other.getUserId());
    }

    @Test
    void createsSessionSendsMessageAndClosesSession() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/chat/sessions")
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"요금제 상담\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.title").value("요금제 상담"))
                .andExpect(jsonPath("$.result.status").value("ACTIVE"))
                .andExpect(jsonPath("$.result.createdAt").isNotEmpty())
                .andReturn();

        JsonNode createBody = objectMapper.readTree(createResult.getResponse().getContentAsByteArray());
        long sessionId = createBody.path("result").path("sessionId").asLong();

        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"가까운 매장 알려줘\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.sessionId").value(sessionId))
                .andExpect(jsonPath("$.result.sequenceNo").value(1))
                .andExpect(jsonPath("$.result.executionStatus").value("RUNNING"))
                .andExpect(jsonPath("$.result.createdAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/chat/sessions")
                        .session(ownerSession)
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.sessions[0].sessionId").value(sessionId))
                .andExpect(jsonPath("$.result.sessions[0].title").value("요금제 상담"));

        mockMvc.perform(patch("/api/v1/chat/sessions/{sessionId}/title", sessionId)
                        .session(otherSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"탈취 시도\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-0"));

        mockMvc.perform(patch("/api/v1/chat/sessions/{sessionId}/close", sessionId)
                        .session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CLOSED"));

        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"추가 질문\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAT409-0"));
    }

    @Test
    void createsAndListsOnlyOwnedGuestSession() throws Exception {
        UUID guestId = persistGuest();
        UUID otherGuestId = persistGuest();
        MockHttpSession guestSession = guestSession(guestId);
        MockHttpSession otherGuestSession = guestSession(otherGuestId);

        MvcResult createResult = mockMvc.perform(post("/api/v1/chat/sessions")
                        .session(guestSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"게스트 상담\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        long sessionId = objectMapper.readTree(createResult.getResponse().getContentAsByteArray())
                .path("result").path("sessionId").asLong();

        mockMvc.perform(get("/api/v1/chat/sessions").session(guestSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.sessions.length()").value(1))
                .andExpect(jsonPath("$.result.sessions[0].sessionId").value(sessionId));

        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(guestSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageSendRequest("게스트 질문"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(guestSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messages.length()").value(1))
                .andExpect(jsonPath("$.result.messages[0].content").value("게스트 질문"));

        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(otherGuestSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-0"));

        mockMvc.perform(patch("/api/v1/chat/sessions/{sessionId}/close", sessionId)
                        .session(otherGuestSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-0"));

        mockMvc.perform(patch("/api/v1/chat/sessions/{sessionId}/close", sessionId)
                        .session(guestSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CLOSED"));
    }

    @Test
    void rejectsMessageExceedingMaximumLength() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/v1/chat/sessions")
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();

        long sessionId = objectMapper.readTree(createResult.getResponse().getContentAsByteArray())
                .path("result").path("sessionId").asLong();

        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + "가".repeat(2001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"));

        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + "가".repeat(2000) + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.sequenceNo").value(1));
    }

    @Test
    void rejectsInvalidSessionListSize() throws Exception {
        assertInvalidSessionListSize("0");
        assertInvalidSessionListSize("51");
        assertInvalidSessionListSize("abc");
    }

    @Test
    void paginatesSessionListWithoutDuplicates() throws Exception {
        for (int index = 1; index <= 3; index++) {
            mockMvc.perform(post("/api/v1/chat/sessions")
                            .session(ownerSession)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"상담 " + index + "\"}"))
                    .andExpect(status().isCreated());
        }

        MvcResult firstPageResult = mockMvc.perform(get("/api/v1/chat/sessions")
                        .session(ownerSession)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.sessions.length()").value(2))
                .andExpect(jsonPath("$.result.hasNext").value(true))
                .andExpect(jsonPath("$.result.nextCursor").isNotEmpty())
                .andReturn();

        JsonNode firstPage = objectMapper.readTree(firstPageResult.getResponse().getContentAsByteArray());
        String cursor = firstPage.path("result").path("nextCursor").asText();
        long secondSessionId = firstPage.path("result").path("sessions").get(1)
                .path("sessionId").asLong();

        mockMvc.perform(get("/api/v1/chat/sessions")
                        .session(ownerSession)
                        .param("size", "2")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.sessions.length()").value(1))
                .andExpect(jsonPath("$.result.sessions[0].sessionId").value(org.hamcrest.Matchers.not(secondSessionId)))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    void paginatesMessageHistoryFromOldestToNewest() throws Exception {
        long sessionId = createChatSession("대화 이력 테스트");
        sendChatMessage(sessionId, "첫 번째 질문");
        sendChatMessage(sessionId, "두 번째 질문");
        sendChatMessage(sessionId, "세 번째 질문");

        mockMvc.perform(patch("/api/v1/chat/sessions/{sessionId}/close", sessionId)
                        .session(ownerSession))
                .andExpect(status().isOk());

        MvcResult firstPageResult = mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(ownerSession)
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messages.length()").value(2))
                .andExpect(jsonPath("$.result.messages[0].sequenceNo").value(2))
                .andExpect(jsonPath("$.result.messages[0].content").value("두 번째 질문"))
                .andExpect(jsonPath("$.result.messages[1].sequenceNo").value(3))
                .andExpect(jsonPath("$.result.messages[1].content").value("세 번째 질문"))
                .andExpect(jsonPath("$.result.nextBeforeSequenceNo").value(2))
                .andExpect(jsonPath("$.result.hasOlderMessages").value(true))
                .andReturn();

        int nextBeforeSequenceNo = objectMapper.readTree(firstPageResult.getResponse().getContentAsByteArray())
                .path("result").path("nextBeforeSequenceNo").asInt();

        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(ownerSession)
                        .param("beforeSequenceNo", String.valueOf(nextBeforeSequenceNo))
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messages.length()").value(1))
                .andExpect(jsonPath("$.result.messages[0].sequenceNo").value(1))
                .andExpect(jsonPath("$.result.messages[0].content").value("첫 번째 질문"))
                .andExpect(jsonPath("$.result.nextBeforeSequenceNo").value(nullValue()))
                .andExpect(jsonPath("$.result.hasOlderMessages").value(false));
    }

    @Test
    void rejectsMessageHistoryWhenActorIsNotOwner() throws Exception {
        long sessionId = createChatSession("소유권 테스트");

        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(otherSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-0"));
    }

    @Test
    void includesGeneratingAssistantMessageInHistory() throws Exception {
        long sessionId = createChatSession("생성 중 메시지 테스트");
        ChatSession chatSession = entityManager.find(ChatSession.class, sessionId);
        entityManager.persist(ChatMessage.builder()
                .session(chatSession)
                .sequenceNo(1)
                .role(ChatMessage.Role.ASSISTANT)
                .messageType(ChatMessage.MessageType.ANSWER)
                .status(ChatMessage.Status.GENERATING)
                .build());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messages.length()").value(1))
                .andExpect(jsonPath("$.result.messages[0].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.result.messages[0].status").value("GENERATING"));
    }

    @Test
    void returnsReplyToMessageId() throws Exception {
        long sessionId = createChatSession("답글 연결 테스트");
        ChatSession chatSession = entityManager.find(ChatSession.class, sessionId);
        ChatMessage question = ChatMessage.builder()
                .session(chatSession)
                .sequenceNo(1)
                .role(ChatMessage.Role.USER)
                .messageType(ChatMessage.MessageType.QUESTION)
                .content("요금제 변경 방법을 알려줘")
                .status(ChatMessage.Status.COMPLETED)
                .build();
        entityManager.persist(question);
        entityManager.flush();

        entityManager.persist(ChatMessage.builder()
                .session(chatSession)
                .sequenceNo(2)
                .replyTo(question)
                .role(ChatMessage.Role.ASSISTANT)
                .messageType(ChatMessage.MessageType.ANSWER)
                .content("요금제 변경 방법을 안내합니다.")
                .status(ChatMessage.Status.COMPLETED)
                .build());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messages[1].replyToMessageId").value(question.getMessageId()));
    }

    @Test
    void returnsJsonMessageMetadataAsJsonValues() throws Exception {
        long sessionId = createChatSession("JSON 메타데이터 테스트");
        ChatSession chatSession = entityManager.find(ChatSession.class, sessionId);
        entityManager.persist(ChatMessage.builder()
                .session(chatSession)
                .sequenceNo(1)
                .role(ChatMessage.Role.ASSISTANT)
                .messageType(ChatMessage.MessageType.STORE_RESULT)
                .content("가까운 매장을 안내합니다.")
                .status(ChatMessage.Status.COMPLETED)
                .answerBasis(ChatMessage.AnswerBasis.GROUNDED)
                .followUps("[\"다른 매장도 보여줘\"]")
                .storeResults("[{\"storeId\":3,\"name\":\"텔미 강남점\"}]")
                .build());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messages[0].followUps[0]").value("다른 매장도 보여줘"))
                .andExpect(jsonPath("$.result.messages[0].storeResults[0].storeId").value(3))
                .andExpect(jsonPath("$.result.messages[0].storeResults[0].name").value("텔미 강남점"));
    }

    @Test
    void keepsHistoryAvailableWhenJsonMetadataHasUnexpectedShape() throws Exception {
        long sessionId = createChatSession("잘못된 JSON 메타데이터 테스트");
        ChatSession chatSession = entityManager.find(ChatSession.class, sessionId);
        entityManager.persist(ChatMessage.builder()
                .session(chatSession)
                .sequenceNo(1)
                .role(ChatMessage.Role.ASSISTANT)
                .messageType(ChatMessage.MessageType.ANSWER)
                .content("정상 메시지")
                .status(ChatMessage.Status.COMPLETED)
                .followUps("[\"정상 후속 질문\"]")
                .storeResults("[{\"storeId\":1}]")
                .build());
        entityManager.persist(ChatMessage.builder()
                .session(chatSession)
                .sequenceNo(2)
                .role(ChatMessage.Role.ASSISTANT)
                .messageType(ChatMessage.MessageType.ANSWER)
                .content("본문은 정상적으로 조회됩니다.")
                .status(ChatMessage.Status.COMPLETED)
                .followUps("{\"items\":[\"잘못된 형태\"]}")
                .storeResults("[1,2,3]")
                .build());
        entityManager.flush();
        entityManager.clear();

        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.messages.length()").value(2))
                .andExpect(jsonPath("$.result.messages[0].content").value("정상 메시지"))
                .andExpect(jsonPath("$.result.messages[0].followUps[0]").value("정상 후속 질문"))
                .andExpect(jsonPath("$.result.messages[0].storeResults[0].storeId").value(1))
                .andExpect(jsonPath("$.result.messages[1].content").value("본문은 정상적으로 조회됩니다."))
                .andExpect(jsonPath("$.result.messages[1].followUps").value(nullValue()))
                .andExpect(jsonPath("$.result.messages[1].storeResults").value(nullValue()));
    }

    @Test
    void rejectsInvalidMessageHistoryParameters() throws Exception {
        long sessionId = createChatSession("파라미터 테스트");

        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(ownerSession)
                        .param("beforeSequenceNo", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"))
                .andExpect(jsonPath("$.result.beforeSequenceNo").isNotEmpty());

        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(ownerSession)
                        .param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"))
                .andExpect(jsonPath("$.result.size").isNotEmpty());
    }

    private long createChatSession(String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/chat/sessions")
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SessionCreateRequest(title))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("result").path("sessionId").asLong();
    }

    private void sendChatMessage(long sessionId, String content) throws Exception {
        mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .session(ownerSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageSendRequest(content))))
                .andExpect(status().isCreated());
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

    private void assertInvalidSessionListSize(String size) throws Exception {
        mockMvc.perform(get("/api/v1/chat/sessions")
                        .session(ownerSession)
                        .param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400-1"))
                .andExpect(jsonPath("$.result.size").isNotEmpty());
    }

    private UUID persistGuest() {
        UUID guestId = UUID.randomUUID();
        Guest guest = Guest.builder()
                .guestId(guestId)
                .lastSeenAt(Instant.now())
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build();
        entityManager.persist(guest);
        entityManager.flush();
        return guestId;
    }

    private MockHttpSession guestSession(UUID guestId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guestId);
        return session;
    }

    private record SessionCreateRequest(String title) {
    }

    private record MessageSendRequest(String content) {
    }
}
