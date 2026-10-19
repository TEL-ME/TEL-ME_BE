package com.telme.notification.repository;

import com.telme.chat.service.ChatActor;
import com.telme.notification.dto.NotificationType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// 사용자 요청은 chat_sessions 조인으로 주인을 확인한다(승계 시 알림도 따라감)
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

    // 종류별 알림 대상 메시지
    private static final Map<NotificationType, String> TARGET_MESSAGE = Map.of(
            NotificationType.ANSWER_ARRIVED,
            "m.status = 'COMPLETED' AND m.message_type IN ('ANSWER', 'CLARIFICATION', 'STORE_RESULT')",
            NotificationType.ANSWER_FAILED,
            "m.status IN ('FAILED', 'TIMEOUT')");

    // 프론트 저장. 이미 있으면 CLIENT로 바꿔 그 알림을 돌려준다
    public Optional<Long> insert(ChatActor actor, long sessionId, long messageId, NotificationType type) {
        return jdbc.queryForList("""
                INSERT INTO notifications (session_id, message_id, type, source)
                SELECT m.session_id, m.message_id, ?, 'CLIENT'
                FROM chat_messages m
                JOIN chat_sessions s ON s.session_id = m.session_id
                WHERE m.message_id = ? AND m.session_id = ? AND %s AND %s
                ON CONFLICT (message_id) DO UPDATE SET source = 'CLIENT'
                RETURNING notification_id
                """.formatted(target(type), owner(actor)),
                Long.class, type.name(), messageId, sessionId, ownerId(actor)).stream().findFirst();
    }

    // 서버 저장. 종료 결과를 전하지 못한 실행의 출력 메시지로 저장한다
    public Optional<Long> insertForExecution(long executionId, NotificationType type) {
        return jdbc.queryForList("""
                INSERT INTO notifications (session_id, message_id, type, source)
                SELECT m.session_id, m.message_id, ?, 'SERVER'
                FROM chat_executions e
                JOIN chat_messages m ON m.message_id = e.output_message_id
                WHERE e.execution_id = ? AND %s
                ON CONFLICT (message_id) DO NOTHING
                RETURNING notification_id
                """.formatted(target(type)),
                Long.class, type.name(), executionId).stream().findFirst();
    }

    // 늦은 구독에 결과를 전한 실행의 서버 알림만 지운다
    public int deleteServerSaved(long executionId) {
        return jdbc.update("""
                DELETE FROM notifications n
                USING chat_executions e
                WHERE e.execution_id = ? AND n.message_id = e.output_message_id AND n.source = 'SERVER'
                """, executionId);
    }

    public Optional<NotificationRow> findById(ChatActor actor, long notificationId) {
        return jdbc.query(SELECT + " WHERE n.notification_id = ? AND " + owner(actor),
                this::read, notificationId, ownerId(actor)).stream().findFirst();
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

    // 채팅방에 들어가면 그 방의 안 읽은 알림을 모두 읽음 처리한다. 바뀐 개수를 돌려준다
    public int markSessionRead(ChatActor actor, long sessionId) {
        return jdbc.update("""
                UPDATE notifications n SET read_at = now()
                FROM chat_sessions s
                WHERE s.session_id = n.session_id AND n.session_id = ? AND n.read_at IS NULL AND %s
                """.formatted(owner(actor)), sessionId, ownerId(actor));
    }

    private static String target(NotificationType type) {
        return "m.role = 'ASSISTANT' AND " + TARGET_MESSAGE.get(type);
    }

    // 고정된 두 조건만 쓴다(사용자 입력 아님)
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
