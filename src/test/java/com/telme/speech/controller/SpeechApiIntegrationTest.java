package com.telme.speech.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.global.common.exception.GeneralException;
import com.telme.member.entity.Guest;
import com.telme.member.entity.User;
import com.telme.speech.exception.SpeechErrorCode;
import com.telme.speech.service.PollySpeechClient;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

// Polly 호출은 대신하고 소유권 확인·응답 형식·캐시를 실제 DB로 확인한다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SpeechApiIntegrationTest {

    private static final byte[] AUDIO = {73, 68, 51};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private PollySpeechClient pollySpeechClient;

    private User owner;
    private MockHttpSession ownerSession;

    @BeforeEach
    void setUp() {
        owner = persistUser("speech-owner");
        ownerSession = memberSession(owner.getUserId());
    }

    @Test
    void 내_답변을_mp3로_돌려주고_링크는_읽지_않는다() throws Exception {
        ChatMessage answer = persistMessage(persistMemberChatSession(owner.getUserId()), 1,
                ChatMessage.Role.ASSISTANT, "요금제는 마이페이지에서 바꿀 수 있어요. https://www.example.com");
        when(pollySpeechClient.synthesize("요금제는 마이페이지에서 바꿀 수 있어요.")).thenReturn(AUDIO);

        mockMvc.perform(get("/api/v1/chat/messages/{messageId}/speech", answer.getMessageId())
                        .session(ownerSession))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/mpeg"))
                .andExpect(header().string("Cache-Control", "max-age=3600, private"))
                .andExpect(header().string("Vary", "Cookie"))
                .andExpect(content().bytes(AUDIO));
    }

    @Test
    void 같은_답변을_다시_들으면_Polly를_다시_부르지_않는다() throws Exception {
        ChatMessage answer = persistMessage(persistMemberChatSession(owner.getUserId()), 1,
                ChatMessage.Role.ASSISTANT, "다시 듣기 테스트입니다.");
        when(pollySpeechClient.synthesize(anyString())).thenReturn(AUDIO);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/v1/chat/messages/{messageId}/speech", answer.getMessageId())
                            .session(ownerSession))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes(AUDIO));
        }
        verify(pollySpeechClient, times(1)).synthesize(anyString());
    }

    @Test
    void 다른_사람의_답변은_찾을_수_없다고_답한다() throws Exception {
        ChatMessage answer = persistMessage(persistMemberChatSession(owner.getUserId()), 1,
                ChatMessage.Role.ASSISTANT, "남의 답변입니다.");
        MockHttpSession otherSession = memberSession(persistUser("speech-other").getUserId());

        mockMvc.perform(get("/api/v1/chat/messages/{messageId}/speech", answer.getMessageId())
                        .session(otherSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-2"));
        verifyNoInteractions(pollySpeechClient);
    }

    @Test
    void 게스트는_자기_대화의_답변만_듣는다() throws Exception {
        UUID guestId = persistGuest();
        ChatMessage answer = persistMessage(persistGuestChatSession(guestId), 1,
                ChatMessage.Role.ASSISTANT, "게스트 답변입니다.");
        when(pollySpeechClient.synthesize(anyString())).thenReturn(AUDIO);

        mockMvc.perform(get("/api/v1/chat/messages/{messageId}/speech", answer.getMessageId())
                        .session(guestSession(guestId)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/chat/messages/{messageId}/speech", answer.getMessageId())
                        .session(ownerSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void 사용자_질문은_읽어_주지_않는다() throws Exception {
        ChatMessage question = persistMessage(persistMemberChatSession(owner.getUserId()), 1,
                ChatMessage.Role.USER, "요금제 바꾸고 싶어요");

        mockMvc.perform(get("/api/v1/chat/messages/{messageId}/speech", question.getMessageId())
                        .session(ownerSession))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SPEECH400-0"));
        verifyNoInteractions(pollySpeechClient);
    }

    @Test
    void Polly_실패는_JSON_오류로_알린다() throws Exception {
        ChatMessage answer = persistMessage(persistMemberChatSession(owner.getUserId()), 1,
                ChatMessage.Role.ASSISTANT, "실패 테스트입니다.");
        when(pollySpeechClient.synthesize(anyString()))
                .thenThrow(new GeneralException(SpeechErrorCode.SYNTHESIS_UNAVAILABLE));

        mockMvc.perform(get("/api/v1/chat/messages/{messageId}/speech", answer.getMessageId())
                        .session(ownerSession))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SPEECH503-0"));
    }

    @Test
    void 세션_없이_요청하면_새_게스트라_남의_답변을_듣지_못한다() throws Exception {
        ChatMessage answer = persistMessage(persistMemberChatSession(owner.getUserId()), 1,
                ChatMessage.Role.ASSISTANT, "회원 답변입니다.");

        mockMvc.perform(get("/api/v1/chat/messages/{messageId}/speech", answer.getMessageId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT404-2"));
        verifyNoInteractions(pollySpeechClient);
    }

    @Test
    void Accept에_audio_mpeg만_보내도_오류는_JSON으로_받는다() throws Exception {
        ChatMessage answer = persistMessage(persistMemberChatSession(owner.getUserId()), 1,
                ChatMessage.Role.ASSISTANT, "남의 답변입니다.");
        MockHttpSession otherSession = memberSession(persistUser("speech-accept").getUserId());

        mockMvc.perform(get("/api/v1/chat/messages/{messageId}/speech", answer.getMessageId())
                        .session(otherSession)
                        .header("Accept", "audio/mpeg"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("CHAT404-2"));
        mockMvc.perform(get("/api/v1/chat/messages/{messageId}/speech", 0)
                        .session(ownerSession)
                        .header("Accept", "audio/mpeg"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.result.messageId").value("메시지 ID는 1 이상이어야 합니다."));
    }

    @Test
    void 메시지_ID는_1_이상이어야_한다() throws Exception {
        mockMvc.perform(get("/api/v1/chat/messages/{messageId}/speech", 0)
                        .session(ownerSession))
                .andExpect(status().isBadRequest());
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
        ChatSession session = ChatSession.builder().userId(userId).title("음성 테스트").build();
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }

    private ChatSession persistGuestChatSession(UUID guestId) {
        ChatSession session = ChatSession.builder().guestId(guestId).title("게스트 음성 테스트").build();
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }

    private ChatMessage persistMessage(ChatSession session, int sequenceNo, ChatMessage.Role role, String content) {
        boolean answer = role == ChatMessage.Role.ASSISTANT;
        ChatMessage message = ChatMessage.builder()
                .session(session)
                .sequenceNo(sequenceNo)
                .role(role)
                .messageType(answer ? ChatMessage.MessageType.ANSWER : ChatMessage.MessageType.QUESTION)
                .content(content)
                .status(answer ? ChatMessage.Status.COMPLETED : null)
                .answerBasis(answer ? ChatMessage.AnswerBasis.GROUNDED : null)
                .completedAt(answer ? Instant.now() : null)
                .build();
        entityManager.persist(message);
        entityManager.flush();
        return message;
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
