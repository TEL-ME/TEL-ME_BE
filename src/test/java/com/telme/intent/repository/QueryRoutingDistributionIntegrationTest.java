package com.telme.intent.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.intent.entity.QueryRouting.Intent;
import com.telme.intent.entity.QueryRouting.Method;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 샘플 데이터와 겹치지 않는 기간을 사용하며 추가한 행은 테스트 종료 시 롤백한다.
@SpringBootTest
@Transactional
class QueryRoutingDistributionIntegrationTest {

    private static final Instant FROM = Instant.parse("2200-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2200-10-02T00:00:00Z");

    @Autowired
    private QueryRoutingRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private long sessionId;
    private int sequence;

    @BeforeEach
    void setUp() {
        UUID guestId = UUID.randomUUID();
        jdbc.update("insert into guests (guest_id, expires_at) values (?, now() + interval '1 day')", guestId);
        sessionId = jdbc.queryForObject(
                "insert into chat_sessions (guest_id, title) values (?, '분류 통계 테스트') returning session_id",
                Long.class, guestId);
    }

    @Test
    void 시작은_포함하고_끝은_제외하며_null_방식도_집계한다() {
        insertRouting("FAQ", "LLM", "2200-10-01T00:00:00Z");
        insertRouting("FAQ", "LLM", "2200-10-01T01:00:00Z");
        insertRouting("STORE", "RULE", "2200-10-01T02:00:00Z");
        insertRouting("BOTH", "RULE", "2200-10-01T03:00:00Z");
        insertRouting("UNKNOWN", null, "2200-10-01T04:00:00Z");
        insertRouting("FAQ", "RULE", "2200-09-30T23:59:59Z");
        insertRouting("FAQ", "RULE", "2200-10-02T00:00:00Z");

        assertThat(repository.countDistribution(FROM, TO)).containsExactlyInAnyOrder(
                new RoutingCount(Intent.FAQ, Method.LLM, 2),
                new RoutingCount(Intent.STORE, Method.RULE, 1),
                new RoutingCount(Intent.BOTH, Method.RULE, 1),
                new RoutingCount(Intent.UNKNOWN, null, 1));
    }

    @Test
    void 같은_시각의_구간은_빈_결과다() {
        insertRouting("FAQ", "LLM", "2200-10-01T00:00:00Z");

        assertThat(repository.countDistribution(FROM, FROM)).isEmpty();
    }

    private void insertRouting(String intent, String method, String createdAt) {
        // 질문 시각과 분류 시각을 달리해 분류 기록의 created_at으로 조회하는지 검증한다.
        long messageId = jdbc.queryForObject("""
                insert into chat_messages (session_id, sequence_no, role, message_type, content, created_at)
                values (?, ?, 'USER', 'QUESTION', '분류 통계 질문', '2199-01-01T00:00:00Z') returning message_id
                """, Long.class, sessionId, ++sequence);
        jdbc.update("""
                insert into query_routings (message_id, intent, method, created_at)
                values (?, ?, ?, cast(? as timestamptz))
                """, messageId, intent, method, createdAt);
    }
}
