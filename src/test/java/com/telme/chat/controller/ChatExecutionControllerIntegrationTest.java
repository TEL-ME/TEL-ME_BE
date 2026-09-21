package com.telme.chat.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.chat.dto.req.ChatMessageSendRequest;
import com.telme.chat.dto.req.ChatSessionCreateRequest;
import com.telme.chat.dto.res.ChatMessageSendResponse;
import com.telme.chat.entity.ChatMessage;
import com.telme.chat.service.ChatActor;
import com.telme.chat.service.ChatAnswer;
import com.telme.chat.service.ChatEmitterRegistry;
import com.telme.chat.service.ChatExecutionService;
import com.telme.chat.service.ChatSessionService;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.member.entity.User;

import jakarta.persistence.EntityManager;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ChatExecutionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ChatSessionService chatSessionService;

    @Autowired
    private ChatExecutionService chatExecutionService;

    @Autowired
    private ChatEmitterRegistry emitterRegistry;

    private MockHttpSession ownerSession;
    private MockHttpSession otherSession;
    private ChatActor owner;

    @BeforeEach
    void setUpActors() {
        User ownerUser = persistUser("subscribe-owner");
        User otherUser = persistUser("subscribe-other");
        owner = new ChatActor(ownerUser.getUserId(), null);

        ownerSession = new MockHttpSession();
        ownerSession.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, ownerUser.getUserId());
        otherSession = new MockHttpSession();
        otherSession.setAttribute(HttpSessionChatActorProvider.USER_ID_ATTRIBUTE, otherUser.getUserId());
    }

    @Test
    void returnsNotFoundWhenSubscriberIsNotOwner() throws Exception {
        long sessionId = createSession();
        long executionId = sendMessage(sessionId, "가까운 매장 알려줘").executionId();

        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/executions/{executionId}/subscribe",
                        sessionId, executionId)
                        .session(otherSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-1"));
    }

    @Test
    void returnsNotFoundWhenSessionIdInPathDoesNotMatchExecution() throws Exception {
        long sessionId = createSession();
        long executionId = sendMessage(sessionId, "가까운 매장 알려줘").executionId();
        long otherSessionId = createSession();

        mockMvc.perform(get("/api/v1/chat/sessions/{sessionId}/executions/{executionId}/subscribe",
                        otherSessionId, executionId)
                        .session(ownerSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-1"));
    }

    @Test
    void immediatelyEmitsCompleteEventWhenExecutionAlreadyFinished() throws Exception {
        long sessionId = createSession();
        ChatMessageSendResponse question = sendMessage(sessionId, "가까운 매장 알려줘");
        chatExecutionService.completeAnswer(question.executionId(), new ChatAnswer(
                ChatMessage.MessageType.ANSWER, "강남역점이 가장 가깝습니다.", null, null, null));

        MvcResult result = mockMvc.perform(get(
                        "/api/v1/chat/sessions/{sessionId}/executions/{executionId}/subscribe",
                        sessionId, question.executionId())
                        .session(ownerSession))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"COMPLETED\"")));
        assertThat(emitterRegistry.isRegistered(question.executionId())).isFalse();
    }

    @Test
    void registersSubscriptionWhileExecutionIsStillRunning() throws Exception {
        long sessionId = createSession();
        ChatMessageSendResponse question = sendMessage(sessionId, "가까운 매장 알려줘");

        mockMvc.perform(get(
                        "/api/v1/chat/sessions/{sessionId}/executions/{executionId}/subscribe",
                        sessionId, question.executionId())
                        .session(ownerSession))
                .andExpect(request().asyncStarted());

        assertThat(emitterRegistry.isRegistered(question.executionId())).isTrue();
        // 실제 서비스에서는 AI 처리가 끝나면서 registry가 자동으로 정리되므로 테스트에서 직접 정리한다.
        emitterRegistry.fail(question.executionId(), "test-cleanup");
    }

    private long createSession() {
        return chatSessionService.createSession(owner, new ChatSessionCreateRequest("구독 테스트")).sessionId();
    }

    private ChatMessageSendResponse sendMessage(long sessionId, String content) {
        return chatSessionService.sendMessage(owner, sessionId, new ChatMessageSendRequest(content));
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
}
