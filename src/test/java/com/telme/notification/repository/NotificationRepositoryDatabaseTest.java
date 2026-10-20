package com.telme.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.telme.chat.service.ChatActor;
import com.telme.notification.dto.NotificationType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 각 테스트는 트랜잭션 롤백으로 데이터에 영향 없음. 사용자를 테스트마다 만들어 시드 데이터와 기존 알림에 영향받지 않는다
@SpringBootTest
@Transactional
class NotificationRepositoryDatabaseTest {

    private static final NotificationType TYPE = NotificationType.ANSWER_ARRIVED;
    private static final NotificationType FAILED = NotificationType.ANSWER_FAILED;

    @Autowired
    private NotificationRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    private long userId;
    private long otherUserId;
    private ChatActor member;
    private UUID guestId;
    private ChatActor guest;
    private int sequence;

    @BeforeEach
    void setUp() {
        // 시드 사용자에 기대지 않고 테스트마다 사용자를 만든다
        userId = user("owner");
        otherUserId = user("other");
        member = new ChatActor(userId, null);
        guestId = UUID.randomUUID();
        jdbc.update("INSERT INTO guests (guest_id, expires_at) VALUES (?, now() + interval '1 day')", guestId);
        guest = new ChatActor(null, guestId);
        sequence = 0;
    }

    @Test
    @DisplayName("내 세션의 완료된 답변은 저장되고, 같은 답변을 다시 저장하면 기존 알림 번호를 돌려준다")
    void 같은_답변은_한_번만_저장한다() {
        long session = memberSession(userId, "요금제 상담");
        long answer = message(session, "ASSISTANT", "ANSWER", "COMPLETED", "5G 요금제는 세 가지가 있습니다.");

        long id = repository.insert(member, session, answer, TYPE).orElseThrow();

        assertThat(repository.insert(member, session, answer, TYPE)).contains(id);
        NotificationRow row = repository.findById(member, id).orElseThrow();
        assertThat(row.messageId()).isEqualTo(answer);
        assertThat(row.sessionTitle()).isEqualTo("요금제 상담");
        assertThat(row.messagePreview()).isEqualTo("5G 요금제는 세 가지가 있습니다.");
        assertThat(row.readAt()).isNull();
    }

    @Test
    @DisplayName("되묻기와 매장 결과도 알림 대상이다")
    void 되묻기와_매장_결과도_저장한다() {
        long session = memberSession(userId, null);
        long clarification = message(session, "ASSISTANT", "CLARIFICATION", "COMPLETED", "어느 지역인가요?");
        long store = message(session, "ASSISTANT", "STORE_RESULT", "COMPLETED", "가까운 매장입니다.");

        assertThat(repository.insert(member, session, clarification, TYPE)).isPresent();
        assertThat(repository.insert(member, session, store, TYPE)).isPresent();
    }

    @Test
    @DisplayName("사용자 메시지, 오류 응답, 생성 중 답변, 다른 세션 번호, 남의 세션은 저장하지 않는다")
    void 알림_대상이_아니면_저장하지_않는다() {
        long session = memberSession(userId, null);
        long question = message(session, "USER", "QUESTION", "COMPLETED", "요금제 알려줘");
        long error = message(session, "ASSISTANT", "ERROR", "COMPLETED", "잠시 후 다시 시도해 주세요.");
        long generating = message(session, "ASSISTANT", "ANSWER", "GENERATING", null);
        long answer = message(session, "ASSISTANT", "ANSWER", "COMPLETED", "답변");
        long otherSession = memberSession(userId, null);
        long othersAnswer = message(memberSession(otherUserId, null), "ASSISTANT", "ANSWER", "COMPLETED", "답변");

        assertThat(repository.insert(member, session, question, TYPE)).isEmpty();
        assertThat(repository.insert(member, session, error, TYPE)).isEmpty();
        assertThat(repository.insert(member, session, generating, TYPE)).isEmpty();
        assertThat(repository.insert(member, otherSession, answer, TYPE)).isEmpty();
        assertThat(repository.insert(member, sessionOf(othersAnswer), othersAnswer, TYPE)).isEmpty();
        assertThat(repository.insert(guest, session, answer, TYPE)).isEmpty();
    }

    @Test
    @DisplayName("실패 알림은 실패·시간 초과 답변만 저장하고, 취소된 답변과 종류가 맞지 않는 메시지는 거절한다")
    void 실패_알림_대상() {
        long session = memberSession(userId, null);
        long failed = message(session, "ASSISTANT", "ERROR", "FAILED", null);
        long timeout = message(session, "ASSISTANT", "ANSWER", "TIMEOUT", null);
        long cancelled = message(session, "ASSISTANT", "ANSWER", "CANCELLED", null);
        long answer = message(session, "ASSISTANT", "ANSWER", "COMPLETED", "답변");

        assertThat(repository.insert(member, session, failed, FAILED)).isPresent();
        assertThat(repository.insert(member, session, timeout, FAILED)).isPresent();
        assertThat(repository.insert(member, session, cancelled, FAILED)).isEmpty();
        assertThat(repository.insert(member, session, answer, FAILED)).isEmpty();
        assertThat(repository.insert(member, session, cancelled, TYPE)).isEmpty();
    }

    @Test
    @DisplayName("서버는 실행의 출력 메시지로 알림을 저장하고, 대상이 아니거나 이미 있으면 저장하지 않는다. 늦은 전달 표시는 숨긴다")
    void 실행_기준_저장과_숨김() {
        long session = memberSession(userId, null);
        long answer = message(session, "ASSISTANT", "ANSWER", "COMPLETED", "답변");
        long execution = execution(session, answer);
        long cancelled = execution(session, message(session, "ASSISTANT", "ANSWER", "CANCELLED", null));

        long id = repository.insertForExecution(execution, TYPE).orElseThrow();
        assertThat(repository.insertForExecution(execution, TYPE)).isEmpty();
        assertThat(repository.insertForExecution(cancelled, FAILED)).isEmpty();
        assertThat(repository.findById(member, id).orElseThrow().messageId()).isEqualTo(answer);

        assertThat(repository.markDelivered(execution)).isEqualTo(1);
        assertThat(repository.findById(member, id)).isEmpty();
        assertThat(repository.insertForExecution(execution, TYPE)).isEmpty();
        assertThat(repository.countUnread(member)).isZero();
        assertThat(repository.markDelivered(cancelled)).isZero();
    }

    @Test
    @DisplayName("늦은 전달이 서버 저장보다 먼저 와도 숨긴 행이 남아 뒤늦은 서버 알림이 생기지 않는다(서버 여러 대에서도 같음)")
    void 늦은_전달이_먼저면_서버_알림이_생기지_않는다() {
        long session = memberSession(userId, null);
        long execution = execution(session, message(session, "ASSISTANT", "ANSWER", "COMPLETED", "답변"));
        long failed = execution(session, message(session, "ASSISTANT", "ERROR", "TIMEOUT", null));

        assertThat(repository.markDelivered(execution)).isEqualTo(1);
        assertThat(repository.markDelivered(failed)).isEqualTo(1);
        assertThat(repository.insertForExecution(execution, TYPE)).isEmpty();
        assertThat(repository.insertForExecution(failed, FAILED)).isEmpty();
        assertThat(repository.findPage(member, Long.MAX_VALUE, 20)).isEmpty();
        assertThat(repository.countUnread(member)).isZero();
    }

    @Test
    @DisplayName("프론트 저장이 우선한다: 서버 저장·늦은 전달 표시가 프론트 알림을 덮거나 숨기지 않고, 숨긴 행도 프론트가 저장하면 다시 보인다")
    void 프론트_저장이_우선한다() {
        long session = memberSession(userId, null);
        long clientFirst = message(session, "ASSISTANT", "ANSWER", "COMPLETED", "프론트가 먼저");
        long clientExecution = execution(session, clientFirst);
        long serverFirst = message(session, "ASSISTANT", "ANSWER", "COMPLETED", "서버가 먼저");
        long serverExecution = execution(session, serverFirst);
        long dismissedFirst = message(session, "ASSISTANT", "ANSWER", "COMPLETED", "숨김이 먼저");
        long dismissedExecution = execution(session, dismissedFirst);

        long clientId = repository.insert(member, session, clientFirst, TYPE).orElseThrow();
        assertThat(repository.insertForExecution(clientExecution, TYPE)).isEmpty();
        assertThat(repository.markDelivered(clientExecution)).isZero();
        assertThat(repository.findById(member, clientId)).isPresent();

        long serverId = repository.insertForExecution(serverExecution, TYPE).orElseThrow();
        assertThat(repository.insert(member, session, serverFirst, TYPE)).contains(serverId);
        assertThat(repository.markDelivered(serverExecution)).isZero();
        assertThat(repository.findById(member, serverId)).isPresent();

        repository.markDelivered(dismissedExecution);
        long reshown = repository.insert(member, session, dismissedFirst, TYPE).orElseThrow();
        assertThat(repository.findById(member, reshown)).isPresent();
        assertThat(repository.countUnread(member)).isEqualTo(3);
    }

    @Test
    @DisplayName("목록은 최신순이고 커서 다음 페이지에 앞 페이지 알림이 다시 나오지 않는다")
    void 목록은_최신순_커서다() {
        long session = memberSession(userId, null);
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
        long roomA = memberSession(userId, null);
        long roomB = memberSession(userId, null);
        notify(member, roomA);
        notify(member, roomA);
        long inB = notify(member, roomB);
        long others = memberSession(otherUserId, null);
        long othersNotification = notify(new ChatActor(otherUserId, null), others);

        assertThat(repository.countUnread(member)).isEqualTo(before + 3);
        assertThat(repository.markSessionRead(member, roomA)).isEqualTo(2);
        assertThat(repository.markSessionRead(member, others)).isZero();

        assertThat(repository.countUnread(member)).isEqualTo(before + 1);
        assertThat(repository.findById(member, inB).orElseThrow().readAt()).isNull();
        assertThat(repository.findById(new ChatActor(otherUserId, null), othersNotification)
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
        jdbc.update("UPDATE chat_sessions SET user_id = ? WHERE guest_id = ? AND user_id IS NULL", userId, guestId);

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

    private long user(String name) {
        return jdbc.queryForObject("INSERT INTO users (name) VALUES (?) RETURNING user_id", Long.class, name);
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

    private long execution(long session, long outputMessageId) {
        long input = message(session, "USER", "QUESTION", "COMPLETED", "질문");
        return jdbc.queryForObject("INSERT INTO chat_executions (session_id, input_message_id, output_message_id,"
                + " status) VALUES (?, ?, ?, 'COMPLETED') RETURNING execution_id", Long.class, session, input, outputMessageId);
    }

    private long sessionOf(long messageId) {
        return jdbc.queryForObject("SELECT session_id FROM chat_messages WHERE message_id = ?", Long.class, messageId);
    }
}
