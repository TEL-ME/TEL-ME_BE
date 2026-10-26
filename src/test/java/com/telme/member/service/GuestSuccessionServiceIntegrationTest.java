package com.telme.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.telme.chat.repository.ChatSessionRepository;
import com.telme.member.entity.Guest;
import com.telme.member.entity.User;
import com.telme.member.repository.GuestRepository;
import com.telme.member.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// @Transactional로 감싸면 테스트 끝에 항상 롤백돼 실제 커밋 여부를 구분 못해서 일부러 안 씀 — 생성한 데이터는 직접 지운다
@SpringBootTest
class GuestSuccessionServiceIntegrationTest {

    @Autowired
    private GuestSuccessionService guestSuccessionService;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private Clock clock;

    @MockitoBean
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID guestId;
    private Long userId;

    @AfterEach
    void cleanUp() {
        if (guestId != null) {
            guestRepository.deleteById(guestId);
        }
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    @Test
    @DisplayName("중간(채팅 승계)에 실패하면 이미 성공한 게스트 승계도 롤백된다")
    void 중간_실패시_게스트_승계까지_롤백된다() {
        Guest guest = guestRepository.save(Guest.issue(Duration.ofDays(30), clock));
        guestId = guest.getGuestId();
        User user = userRepository.save(
                User.builder().email("guest-succession-tx-" + UUID.randomUUID() + "@example.com").build());
        userId = user.getUserId();

        doThrow(new RuntimeException("강제 실패 — 채팅 승계 중")).when(chatSessionRepository).succeedGuestSessions(any(), any());

        assertThatThrownBy(() -> guestSuccessionService.succeedGuest(guestId, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("강제 실패 — 채팅 승계 중");

        Object rawMergedUserId = jdbcTemplate.queryForObject(
                "SELECT merged_user_id FROM guests WHERE guest_id = ?", Object.class, guestId);
        assertThat(rawMergedUserId).isNull();
        assertThat(guestRepository.findById(guestId).orElseThrow().getMergedUser()).isNull();
    }
}
