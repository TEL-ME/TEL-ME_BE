package com.telme.chat.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

        mockMvc.perform(patch("/api/v1/chat/sessions/{sessionId}/close", sessionId)
                        .session(otherGuestSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-0"));
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
}
