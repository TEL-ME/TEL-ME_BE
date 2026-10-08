package com.telme.chat.guard;

import com.telme.chat.service.ChatActor;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 기존 제한 상태를 읽기만 한다. 조회 과정에서 행 생성, 잠금, 만료 초기화나 승계 저장을 하지 않는다. */
@Repository
@RequiredArgsConstructor
public class JdbcInputGuardStatusStore {

    private final JdbcTemplate jdbc;

    /** 회원 본인 종료 시각을 먼저, 승계 게스트 종료 시각은 내림차순으로 반환한다. */
    public List<Instant> findRestrictionEnds(ChatActor actor) {
        return jdbc.query(
                """
                WITH identity AS (
                    SELECT coalesce(CAST(? AS bigint), (
                        SELECT merged_user_id FROM guests WHERE guest_id = ?
                    )) AS user_id, CAST(? AS uuid) AS guest_id
                )
                SELECT s.restriction_until
                FROM chat_input_guard_states s CROSS JOIN identity i
                WHERE s.restriction_until IS NOT NULL AND (
                    (i.user_id IS NOT NULL AND (
                        s.user_id = i.user_id OR s.guest_id IN (
                            SELECT guest_id FROM guests WHERE merged_user_id = i.user_id
                        )
                    )) OR (i.user_id IS NULL AND s.guest_id = i.guest_id)
                )
                ORDER BY CASE WHEN s.user_id = i.user_id THEN 0 ELSE 1 END,
                    s.restriction_until DESC
                """,
                (rs, row) -> rs.getTimestamp("restriction_until").toInstant(),
                actor.userId(),
                actor.guestId(),
                actor.guestId()
        );
    }
}
