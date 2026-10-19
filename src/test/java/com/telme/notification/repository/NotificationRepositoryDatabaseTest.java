package com.telme.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.service.ChatActor;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 각 테스트는 트랜잭션 롤백으로 데이터에 영향 없음. 로컬 DB에 쌓인 알림이 있어도 깨지지 않게 개수는 차이로 본다
@SpringBootTest
@Transactional
class NotificationRepositoryDatabaseTest {

    private static final String TYPE = "ANSWER_ARRIVED";
    private static final long USER_ID = 1L;
    private static final long OTHER_USER_ID = 2L;

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private final ChatActor member = new ChatActor(USER_ID, null);
    private UUID guestId;
    private ChatActor guest;
    private int sequence;

    @BeforeEach
    void setUp() {
        guestId = UUID.randomUUID();
        jdbc.update("INSERT INTO guests (guest_id, expires_at) VALUES (?, now() + interval '1 day')", guestId);
        guest = new ChatActor(null, guestId);
        sequence = 0;
    }

    @Test
    @DisplayName("내 세션의 완료된 답변은 저장되고, 같은 답변을 다시 저장하면 새로 생기지 않는다")
    void 같은_답변은_한_번만_저장한다() {
        long session = memberSession(USER_ID, "요금제 상담");
        long answer = message(session, "ASSISTANT", "ANSWER", "COMPLETED", "5G 요금제는 세 가지가 있습니다.");

        long id = repository.insert(member, session, answer, TYPE).orElseThrow();

        assertThat(repository.insert(member, session, answer, TYPE)).isEmpty();
        NotificationRow row = repository.findByMessageId(member, answer).orElseThrow();
        assertThat(row.notificationId()).isEqualTo(id);
        assertThat(row.sessionTitle()).isEqualTo("요금제 상담");
        assertThat(row.messagePreview()).isEqualTo("5G 요금제는 세 가지가 있습니다.");
        assertThat(row.readAt()).isNull();
    }

    @Test
    @DisplayName("되묻기와 매장 결과도 알림 대상이다")
    void 되묻기와_매장_결과도_저장한다() {
        long session = memberSession(USER_ID, null);
        long clarification = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED", "어느 지역인가요?");
        long store = message(session, "ASSISTANT", "STORE_RESULT", "COMPLETED", "가까운 매장입니다.");

        assertThat(repository.insert(member, session, clarification, TYPE)).isPresent();
        assertThat(repository.insert(member, session, store, TYPE)).isPresent();
    }

    @Test
    @DisplayName("사용자 메시지, 오류 응답, 생성 중 답변, 다른 세션 번호, 남의 세션은 저장하지 않는다")
    void 알림_대상이_아니면_저장하지_않는다() {
        long session = memberSession(USER_ID, null);
        long question = message(session, "USER", "QUESTION", "COMPLETED", "요금제 알려줘");
        long error = message(session, "ASSISTANT", "ERROR", "COMPLETED", "잠시 후 다시 시도해 주세요.");
        long generating = message(session, "ASSISTANT", "ANSWER", "GENERATING", null);
        long answer = message(session, "ASSISTANT", "ANSWER", "COMPLETED", "답변");
        long otherSession = memberSession(USER_ID, null);
        long othersAnswer = message(memberSession(OTHER_USER_ID, null), "ASSISTANT", "ANSWER", "COMPLETED", "답변");

        assertThat(repository.insert(member, session, question, TYPE)).isEmpty();
        assertThat(repository.insert(member, session, error, TYPE)).isEmpty();
        assertThat(repository.insert(member, session, generating, TYPE)).isEmpty();
        assertThat(repository.insert(member, otherSession, answer, TYPE)).isEmpty();
        assertThat(repository.insert(member, sessionOf(othersAnswer), othersAnswer, TYPE)).isEmpty();
        assertThat(repository.insert(guest, session, answer, TYPE)).isEmpty();
    }

    @Test
    @DisplayName("목록은 최신순이고 커서 다음 페이지에 앞 페이지 알림이 다시 나오지 않는다")
    void 목록은_최신순_커서다() {
        long session = memberSession(USER_ID, null);
        long first = notify(member, session);
        long second = notify(member, session);
        long third = notify(member, session);

        var page = repository.findPage(member, Long.MAX_VALUE, 2);
        assertThat(page).extracting(NotificationRow::notificationId).containsExactly(third, second);

        var next = repository.findPage(member, second, 2);
        assertThat(next).extracting(NotificationRow::notificationId).startsWith(first);
    }

    @Test
    @DisplayName("채팅방 읽음 처리는 그 방 알림만 읽고, 남의 방은 바꾸지 않는다")
    void 방_읽음은_그_방만_바꾼다() {
        long before = repository.countUnread(member);
        long roomA = memberSession(USER_ID, null);
        long roomB = memberSession(USER_ID, null);
        notify(member, roomA);
        notify(member, roomA);
        long inB = notify(member, roomB);
        long others = memberSession(OTHER_USER_ID, null);
        long othersNotification = notify(new ChatActor(OTHER_USER_ID, null), others);

        assertThat(repository.countUnread(member)).isEqualTo(before + 3);
        assertThat(repository.markSessionRead(member, roomA)).isEqualTo(2);
        assertThat(repository.markSessionRead(member, others)).isZero();

        assertThat(repository.countUnread(member)).isEqualTo(before + 1);
        assertThat(repository.findById(member, inB).orElseThrow().readAt()).isNull();
        assertThat(repository.findById(new ChatActor(OTHER_USER_ID, null), othersNotification)
                .orElseThrow().readAt()).isNull();
    }

    @Test
    @DisplayName("게스트 알림은 로그인 승계 뒤 회원에게 보이고 읽음 상태도 이어진다")
    void 게스트_알림은_승계된다() {
        long before = repository.countUnread(member);
        long session = guestSession();
        long read = notify(guest, session);
        long unread = notify(guest, session);
        jdbc.update("UPDATE notifications SET read_at = now() WHERE notification_id = ?", read);
        assertThat(repository.countUnread(guest)).isEqualTo(1);

        // GuestSuccessionService가 세션에 하는 일과 같다
        jdbc.update("UPDATE chat_sessions SET user_id = ? WHERE guest_id = ? AND user_id IS NULL", USER_ID, guestId);

        assertThat(repository.findPage(member, Long.MAX_VALUE, 2))
                .extracting(NotificationRow::notificationId).containsExactly(unread, read);
        assertThat(repository.countUnread(member)).isEqualTo(before + 1);
        assertThat(repository.findPage(guest, Long.MAX_VALUE, 20)).isEmpty();
        assertThat(repository.countUnread(guest)).isZero();
    }

    private long notify(ChatActor actor, long session) {
        long answer = message(session, "ASSISTANT", "ANSWER", "COMPLETED", "답변");
        return repository.insert(actor, session, answer, TYPE).orElseThrow();
    }

    private long memberSession(long userId, String title) {
        return jdbc.queryForObject("INSERT INTO chat_sessions (user_id, title) VALUES (?, ?) RETURNING session_id",
                Long.class, userId, title);
    }

    private long guestSession() {
        return jdbc.queryForObject("INSERT INTO chat_sessions (guest_id) VALUES (?) RETURNING session_id",
                Long.class, guestId);
    }

    private long message(long session, String role, String type, String status, String content) {
        return jdbc.queryForObject("INSERT INTO chat_messages (session_id, sequence_no, role, message_type, status,"
                        + " content) VALUES (?, ?, ?, ?, ?, ?) RETURNING message_id",
                Long.class, session, ++sequence, role, type, status, content);
    }

    private long sessionOf(long messageId) {
        return jdbc.queryForObject("SELECT session_id FROM chat_messages WHERE message_id = ?", Long.class, messageId);
    }
}
