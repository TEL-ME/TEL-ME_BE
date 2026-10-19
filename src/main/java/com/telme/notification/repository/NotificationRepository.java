package com.telme.notification.repository;

import com.telme.chat.service.ChatActor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// 모든 조회·변경은 chat_sessions 조인으로 현재 회원·게스트의 세션인지 확인한다.
// 로그인 승계로 세션 주인이 바뀌면 알림도 함께 따라간다
@Repository
@RequiredArgsConstructor
public class NotificationRepository {
    // 목록 본문으로 쓸 답변 앞부분 길이. 자르는 길이는 응답 변환에서 정한다
    public static final int PREVIEW_LENGTH = 100;

    private final JdbcTemplate jdbc;

    private static final String SELECT = """
            SELECT n.notification_id, n.type, n.session_id, n.message_id, s.title,
                   LEFT(m.content, %d) AS preview, n.created_at, n.read_at
            FROM notifications n
            JOIN chat_sessions s ON s.session_id = n.session_id
            JOIN chat_messages m ON m.message_id = n.message_id
            """.formatted(PREVIEW_LENGTH);

    // 알림이 울릴 수 있는 메시지: 완료된 챗봇 답변·되묻기·매장 결과(오류 응답 제외)
    private static final String NOTIFIABLE_MESSAGE = "m.role = 'ASSISTANT' AND m.status = 'COMPLETED'"
            + " AND m.message_type IN ('ANSWER', 'CLARIFICATION', 'STORE_RESULT')";

    // 주인 확인과 저장을 한 문장으로 한다. 저장 대상이 아니거나 이미 저장된 메시지면 비어 있다
    public Optional<Long> insert(ChatActor actor, long sessionId, long messageId, String type) {
        return jdbc.queryForList("""
                INSERT INTO notifications (session_id, message_id, type)
                SELECT m.session_id, m.message_id, ?
                FROM chat_messages m
                JOIN chat_sessions s ON s.session_id = m.session_id
                WHERE m.message_id = ? AND m.session_id = ? AND %s AND %s
                ON CONFLICT (message_id) DO NOTHING
                RETURNING notification_id
                """.formatted(NOTIFIABLE_MESSAGE, owner(actor)),
                Long.class, type, messageId, sessionId, ownerId(actor)).stream().findFirst();
    }

    public Optional<NotificationRow> findById(ChatActor actor, long notificationId) {
        return jdbc.query(SELECT + " WHERE n.notification_id = ? AND " + owner(actor),
                this::read, notificationId, ownerId(actor)).stream().findFirst();
    }

    public Optional<NotificationRow> findByMessageId(ChatActor actor, long messageId) {
        return jdbc.query(SELECT + " WHERE n.message_id = ? AND " + owner(actor),
                this::read, messageId, ownerId(actor)).stream().findFirst();
    }

    // PK 커서. 첫 조회는 Long.MAX_VALUE
    public List<NotificationRow> findPage(ChatActor actor, long beforeId, int limit) {
        return jdbc.query(SELECT + " WHERE n.notification_id < ? AND " + owner(actor)
                        + " ORDER BY n.notification_id DESC LIMIT ?",
                this::read, beforeId, ownerId(actor), limit);
    }

    public long countUnread(ChatActor actor) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM notifications n
                JOIN chat_sessions s ON s.session_id = n.session_id
                WHERE n.read_at IS NULL AND %s
                """.formatted(owner(actor)), Long.class, ownerId(actor));
        return count == null ? 0 : count;
    }

    public boolean ownsSession(ChatActor actor, long sessionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM chat_sessions s WHERE s.session_id = ? AND " + owner(actor) + ")",
                Boolean.class, sessionId, ownerId(actor)));
    }

    // 채팅방에 들어가면 그 방의 안 읽은 알림을 모두 읽음 처리한다. 바뀐 개수를 돌려준다
    public int markSessionRead(ChatActor actor, long sessionId) {
        return jdbc.update("""
                UPDATE notifications n SET read_at = now()
                FROM chat_sessions s
                WHERE s.session_id = n.session_id AND n.session_id = ? AND n.read_at IS NULL AND %s
                """.formatted(owner(actor)), sessionId, ownerId(actor));
    }

    // 고정된 두 조건만 고른다. 사용자 입력 SQL이 아니다. 승계된 게스트 세션은 회원 조건으로만 보인다
    private static String owner(ChatActor actor) {
        return actor.isMember() ? "s.user_id = ?" : "s.user_id IS NULL AND s.guest_id = ?";
    }

    private static Object ownerId(ChatActor actor) {
        return actor.isMember() ? actor.userId() : actor.guestId();
    }

    private NotificationRow read(ResultSet rs, int rowNum) throws SQLException {
        return new NotificationRow(
                rs.getLong("notification_id"),
                rs.getString("type"),
                rs.getLong("session_id"),
                rs.getLong("message_id"),
                rs.getString("title"),
                rs.getString("preview"),
                instant(rs.getTimestamp("created_at")),
                instant(rs.getTimestamp("read_at")));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
