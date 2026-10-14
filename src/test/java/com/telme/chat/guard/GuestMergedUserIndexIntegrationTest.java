package com.telme.chat.guard;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.service.ChatActor;
import com.telme.member.entity.Guest;
import com.telme.member.entity.User;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"llm.provider=fake", "chat.title.enabled=false"})
@Transactional
class GuestMergedUserIndexIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-10-14T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcInputGuardStatusStore statusStore;

    @Autowired
    private JdbcInputGuardStore guardStore;

    @Test
    @DisplayName("Flyway로 추가한 승계 게스트 인덱스는 유효한 비유니크 부분 인덱스다")
    void 승계_게스트_인덱스는_비유니크이며_NULL을_제외한다() {
        var index = jdbc.queryForMap("""
                SELECT i.indisunique, i.indisvalid, i.indisready,
                    pg_get_expr(i.indpred, i.indrelid) AS predicate,
                    pg_get_indexdef(i.indexrelid, 1, true) AS indexed_column,
                    am.amname AS access_method
                FROM pg_index i
                JOIN pg_class c ON c.oid = i.indexrelid
                JOIN pg_am am ON am.oid = c.relam
                WHERE i.indrelid = 'guests'::regclass AND c.relname = 'idx_guest_merged_user'
                """);

        assertThat(index.get("indisunique")).isEqualTo(false);
        assertThat(index.get("indisvalid")).isEqualTo(true);
        assertThat(index.get("indisready")).isEqualTo(true);
        assertThat(index.get("predicate")).isEqualTo("(merged_user_id IS NOT NULL)");
        assertThat(index.get("indexed_column")).isEqualTo("merged_user_id");
        assertThat(index.get("access_method")).isEqualTo("btree");
    }

    @Test
    @DisplayName("한 회원에게 여러 게스트가 승계돼도 조회와 실제 제한 승계는 가장 늦은 유효 제한을 선택한다")
    void 여러_게스트의_승계와_기존_제한_선택을_허용한다() {
        User member = 회원을_저장한다();
        Guest first = 게스트를_저장한다(member);
        Guest second = 게스트를_저장한다(member);
        Guest unrelated = 게스트를_저장한다(회원을_저장한다());
        Guest unmerged = 게스트를_저장한다(null);
        제한을_저장한다(first, NOW.plusSeconds(20));
        제한을_저장한다(second, NOW.plusSeconds(60));
        제한을_저장한다(unrelated, NOW.plusSeconds(90));
        제한을_저장한다(unmerged, NOW.plusSeconds(120));
        // 만료된 게스트의 승계 이력을 제외하는 정책을 인덱스 조건에 섞지 않는다.
        jdbc.update("UPDATE guests SET expires_at = ? WHERE guest_id = ?",
                Timestamp.from(NOW.minusSeconds(1)), second.getGuestId());
        ChatActor actor = new ChatActor(member.getUserId(), null);

        assertThat(statusStore.findRestrictionEnds(actor))
                .containsExactly(NOW.plusSeconds(60), NOW.plusSeconds(20));
        var inherited = guardStore.resetExpiredAndInherit(guardStore.lock(actor, NOW), NOW);

        assertThat(inherited.restrictionUntil()).isEqualTo(NOW.plusSeconds(60));
        assertThat(statusStore.findRestrictionEnds(new ChatActor(null, first.getGuestId())).getFirst())
                .isEqualTo(inherited.restrictionUntil());
    }

    @Test
    @DisplayName("미승계 게스트가 회원에게 승계되면 인덱스가 갱신되고 조회와 전송의 승계 범위에 포함된다")
    void 새_승계_관계가_조회와_제한_승계에_즉시_반영된다() {
        User member = 회원을_저장한다();
        Guest guest = 게스트를_저장한다(null);
        제한을_저장한다(guest, NOW.plusSeconds(60));
        ChatActor memberActor = new ChatActor(member.getUserId(), null);
        ChatActor guestActor = new ChatActor(null, guest.getGuestId());

        assertThat(statusStore.findRestrictionEnds(memberActor)).isEmpty();
        assertThat(statusStore.findRestrictionEnds(guestActor)).containsExactly(NOW.plusSeconds(60));

        jdbc.update("UPDATE guests SET merged_user_id = ?, merged_at = ? WHERE guest_id = ?",
                member.getUserId(), Timestamp.from(NOW), guest.getGuestId());

        assertThat(statusStore.findRestrictionEnds(memberActor)).containsExactly(NOW.plusSeconds(60));
        var inherited = guardStore.resetExpiredAndInherit(guardStore.lock(guestActor, NOW), NOW);

        assertThat(inherited.userId()).isEqualTo(member.getUserId());
        assertThat(inherited.restrictionUntil()).isEqualTo(NOW.plusSeconds(60));
    }

    private User 회원을_저장한다() {
        User user = User.builder()
                .email("merged-index-" + UUID.randomUUID() + "@example.com")
                .name("승계 인덱스 검증 사용자")
                .build();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Guest 게스트를_저장한다(User member) {
        Guest guest = Guest.builder()
                .guestId(UUID.randomUUID())
                .expiresAt(NOW.plusSeconds(86400))
                .lastSeenAt(NOW)
                .mergedUser(member)
                .build();
        entityManager.persist(guest);
        entityManager.flush();
        return guest;
    }

    private void 제한을_저장한다(Guest guest, Instant until) {
        jdbc.update("""
                INSERT INTO chat_input_guard_states (
                    guest_id, counting_from_at, restriction_started_at, restriction_until, updated_at
                ) VALUES (?, ?, ?, ?, ?)
                """, guest.getGuestId(), Timestamp.from(Instant.EPOCH), Timestamp.from(NOW),
                Timestamp.from(until), Timestamp.from(NOW));
    }
}
