package com.telme.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.entity.ChatSession;
import com.telme.member.entity.Guest;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ChatSessionRepositoryTest {

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void 게스트_세션을_회원에게_승계한다() {
        Guest guest = createGuest();
        User user = createUser();
        ChatSession session1 = createGuestSession(guest.getGuestId());
        ChatSession session2 = createGuestSession(guest.getGuestId());

        int updated = chatSessionRepository.succeedGuestSessions(guest.getGuestId(), user.getUserId());

        assertThat(updated).isEqualTo(2);
        entityManager.clear();
        assertThat(entityManager.find(ChatSession.class, session1.getSessionId()).getUserId())
                .isEqualTo(user.getUserId());
        assertThat(entityManager.find(ChatSession.class, session2.getSessionId()).getUserId())
                .isEqualTo(user.getUserId());
        // guest_id는 이력 보존을 위해 그대로 남는다
        assertThat(entityManager.find(ChatSession.class, session1.getSessionId()).getGuestId())
                .isEqualTo(guest.getGuestId());
    }

    @Test
    void 이미_userId가_있는_세션은_건드리지_않는다() {
        Guest guest = createGuest();
        User alreadyOwner = createUser();
        User newUser = createUser();
        ChatSession alreadySucceeded = ChatSession.builder()
                .userId(alreadyOwner.getUserId())
                .guestId(guest.getGuestId())
                .build();
        entityManager.persist(alreadySucceeded);
        entityManager.flush();

        int updated = chatSessionRepository.succeedGuestSessions(guest.getGuestId(), newUser.getUserId());

        assertThat(updated).isZero();
        entityManager.clear();
        assertThat(entityManager.find(ChatSession.class, alreadySucceeded.getSessionId()).getUserId())
                .isEqualTo(alreadyOwner.getUserId());
    }

    @Test
    void 다른_guestId의_세션은_건드리지_않는다() {
        Guest targetGuest = createGuest();
        Guest otherGuest = createGuest();
        User user = createUser();
        ChatSession otherSession = createGuestSession(otherGuest.getGuestId());

        chatSessionRepository.succeedGuestSessions(targetGuest.getGuestId(), user.getUserId());

        entityManager.clear();
        assertThat(entityManager.find(ChatSession.class, otherSession.getSessionId()).getUserId()).isNull();
    }

    private Guest createGuest() {
        Guest guest = Guest.issue(Duration.ofDays(30), Clock.systemUTC());
        entityManager.persist(guest);
        entityManager.flush();
        return guest;
    }

    private User createUser() {
        User user = User.builder()
                .email("succeed-" + UUID.randomUUID() + "@example.com")
                .name("test")
                .build();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private ChatSession createGuestSession(UUID guestId) {
        ChatSession session = ChatSession.builder()
                .guestId(guestId)
                .build();
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }
}
