package com.telme.member.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.chat.service.HttpSessionChatActorProvider;
import com.telme.feedback.dto.FeedbackModels.Actor;
import com.telme.feedback.dto.FeedbackModels.Input;
import com.telme.feedback.dto.FeedbackModels.Rating;
import com.telme.feedback.service.FeedbackService;
import com.telme.member.dto.req.LoginRequest;
import com.telme.member.entity.Guest;
import com.telme.member.entity.User;
import com.telme.member.repository.GuestRepository;
import com.telme.member.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** telme.feedback.enabled=true일 때 FeedbackStore 빈이 실제로 연결돼 로그인 승계까지 이어지는지 확인한다. */
@SpringBootTest(properties = "telme.feedback.enabled=true")
@Transactional
class MemberAuthFeedbackSuccessionIntegrationTest {

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Clock clock;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("피드백 기능이 켜져 있으면 로그인 후 게스트 피드백의 user_id가 채워진다")
    void 로그인시_게스트_피드백이_승계된다() {
        Guest guest = Guest.issue(Duration.ofDays(30), clock);
        guestRepository.save(guest);
        ChatSession guestSession = ChatSession.builder().guestId(guest.getGuestId()).build();
        entityManager.persist(guestSession);
        ChatMessage answer = ChatMessage.builder()
                .session(guestSession)
                .sequenceNo(1)
                .role(ChatMessage.Role.ASSISTANT)
                .messageType(ChatMessage.MessageType.ANSWER)
                .status(ChatMessage.Status.COMPLETED)
                .build();
        entityManager.persist(answer);
        entityManager.flush();

        feedbackService.save(
                answer.getMessageId(),
                new Actor(null, guest.getGuestId()),
                new Input(Rating.LIKE, null, null));

        MockHttpSession httpSession = new MockHttpSession();
        httpSession.setAttribute(HttpSessionChatActorProvider.GUEST_ID_ATTRIBUTE, guest.getGuestId());
        MockHttpServletRequest httpRequest = new MockHttpServletRequest();
        httpRequest.setSession(httpSession);

        String email = "feedback-succeed-" + UUID.randomUUID() + "@example.com";
        User user = User.builder().email(email).passwordHash(passwordEncoder.encode("password123")).build();
        userRepository.save(user);

        memberAuthService.login(new LoginRequest(email, "password123"), httpRequest, new MockHttpServletResponse());

        Long feedbackUserId = jdbc.queryForObject(
                "SELECT user_id FROM message_feedback WHERE message_id=?", Long.class, answer.getMessageId());
        UUID feedbackGuestId = jdbc.queryForObject(
                "SELECT guest_id FROM message_feedback WHERE message_id=?", UUID.class, answer.getMessageId());
        assertThat(feedbackUserId).isEqualTo(user.getUserId());
        assertThat(feedbackGuestId).isEqualTo(guest.getGuestId());
    }
}
