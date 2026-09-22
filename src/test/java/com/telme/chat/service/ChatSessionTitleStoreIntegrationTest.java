package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.entity.ChatSession;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ChatSessionTitleStoreIntegrationTest {

    @Autowired
    private ChatSessionTitleStore titleStore;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesOnlyWhenSessionTitleIsMissing() {
        ChatSession untitled = createSession(null);
        ChatSession blankTitled = createSession(" ");
        ChatSession manuallyTitled = createSession("직접 지정한 제목");

        assertThat(titleStore.saveIfMissing(untitled.getSessionId(), "자동 생성 제목")).isTrue();
        assertThat(titleStore.saveIfMissing(blankTitled.getSessionId(), "빈 제목 대체")).isTrue();
        assertThat(titleStore.saveIfMissing(manuallyTitled.getSessionId(), "덮어쓸 제목")).isFalse();

        entityManager.clear();
        assertThat(entityManager.find(ChatSession.class, untitled.getSessionId()).getTitle())
                .isEqualTo("자동 생성 제목");
        assertThat(entityManager.find(ChatSession.class, blankTitled.getSessionId()).getTitle())
                .isEqualTo("빈 제목 대체");
        assertThat(entityManager.find(ChatSession.class, manuallyTitled.getSessionId()).getTitle())
                .isEqualTo("직접 지정한 제목");
    }

    private ChatSession createSession(String title) {
        User user = User.builder()
                .email("title-" + UUID.randomUUID() + "@example.com")
                .name("title")
                .build();
        entityManager.persist(user);
        ChatSession session = ChatSession.builder()
                .userId(user.getUserId())
                .title(title)
                .build();
        entityManager.persist(session);
        entityManager.flush();
        return session;
    }
}
