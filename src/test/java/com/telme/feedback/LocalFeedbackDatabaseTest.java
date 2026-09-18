package com.telme.feedback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.telme.feedback.dto.FeedbackModels.Actor;
import com.telme.feedback.dto.FeedbackModels.Input;
import com.telme.feedback.dto.FeedbackModels.Rating;
import com.telme.feedback.dto.FeedbackModels.Reason;
import com.telme.feedback.repository.FeedbackStore;
import com.telme.feedback.repository.JdbcFeedbackStore;
import com.telme.feedback.service.FeedbackService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** 기존 로컬 DB 안의 무작위 전용 스키마만 생성/삭제한다. public 테이블은 사용하지 않는다. */
@EnabledIfEnvironmentVariable(named = "TELME_DB_TESTS", matches = "true")
class LocalFeedbackDatabaseTest {
    JdbcTemplate admin, jdbc;
    FeedbackService feedback;
    TransactionTemplate transaction;
    String schema;
    final Actor owner = new Actor(1L, null);
    long session, answer;

    @BeforeEach
    void setup() throws Exception {
        String port = System.getenv().getOrDefault("POSTGRES_PORT", "5432");
        if (!port.matches("[0-9]+")) {
            throw new IllegalArgumentException("Invalid local port");
        }
        String url = "jdbc:postgresql://127.0.0.1:" + port + "/telme";
        String user = System.getenv().getOrDefault("POSTGRES_USER", "telme");
        String password = System.getenv().getOrDefault("POSTGRES_PASSWORD", "telme");
        admin = new JdbcTemplate(new DriverManagerDataSource(url, user, password));
        schema = "seohee_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public");
        admin.execute("CREATE SCHEMA " + schema);
        var ds =
                new DriverManagerDataSource(
                        url + "?currentSchema=" + schema + ",public", user, password);
        jdbc = new JdbcTemplate(ds);
        for (String file : List.of("init-schema.sql")) {
            try (var stream = getClass().getResourceAsStream("/consult-fixtures/" + file)) {
                jdbc.execute(
                        new String(
                                Objects.requireNonNull(stream).readAllBytes(),
                                StandardCharsets.UTF_8));
            }
        }
        jdbc.execute("INSERT INTO users(user_id,name) VALUES (1,'test owner'),(2,'test other')");
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        transaction = tx;
        var target = new FeedbackService(new JdbcFeedbackStore(jdbc, tx));
        var interceptor = new org.springframework.transaction.interceptor.TransactionInterceptor();
        interceptor.setTransactionManager(tx.getTransactionManager());
        interceptor.setTransactionAttributeSource(
                new org.springframework.transaction.annotation
                        .AnnotationTransactionAttributeSource());
        var proxy = new org.springframework.aop.framework.ProxyFactory(target);
        proxy.addAdvice(interceptor);
        feedback = (FeedbackService) proxy.getProxy();
        session = session(1L, null);
        answer = message(session, "ASSISTANT", "ANSWER", "COMPLETED");
    }

    @AfterEach
    void cleanup() {
        if (admin != null && schema != null) {
            admin.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    long session(Long user, UUID guest) {
        if (guest != null) {
            jdbc.update(
                    "INSERT INTO guests(guest_id,expires_at) VALUES (?,now()+interval '1 day') ON"
                            + " CONFLICT DO NOTHING",
                    guest);
        }
        return jdbc.queryForObject(
                "INSERT INTO chat_sessions(user_id,guest_id) VALUES (?,?) RETURNING session_id",
                Long.class,
                user,
                guest);
    }

    long message(long sid, String role, String type, String status) {
        return jdbc.queryForObject(
                "INSERT INTO chat_messages(session_id,sequence_no,role,message_type,status,content)"
                    + " VALUES (?,(SELECT coalesce(max(sequence_no),0)+1 FROM chat_messages WHERE"
                    + " session_id=?),?,?,?,'test') RETURNING message_id",
                Long.class,
                sid,
                sid,
                role,
                type,
                status);
    }

    Input like() {
        return new Input(Rating.LIKE, null, null);
    }

    @Test
    void feedbackCreateReadUpdateCancelAndRepeatCancel() {
        var first = feedback.save(answer, owner, like());
        var second =
                feedback.save(
                        answer, owner, new Input(Rating.DISLIKE, Reason.WRONG_INFO, "  정보 오류  "));
        assertEquals(first.id(), second.id());
        assertEquals("정보 오류", feedback.get(answer, owner).orElseThrow().input().comment());
        assertEquals(
                1, jdbc.queryForObject("SELECT count(*) FROM message_feedback", Integer.class));
        feedback.cancel(answer, owner);
        feedback.cancel(answer, owner);
        assertTrue(feedback.get(answer, owner).isEmpty());
    }

    @Test
    void otherUserCannotReadModifyOrDelete() {
        feedback.save(answer, owner, like());
        var other = new Actor(2L, null);
        assertThrows(FeedbackStore.TargetUnavailable.class, () -> feedback.get(answer, other));
        assertThrows(
                FeedbackStore.TargetUnavailable.class, () -> feedback.save(answer, other, like()));
        assertThrows(FeedbackStore.TargetUnavailable.class, () -> feedback.cancel(answer, other));
        assertTrue(feedback.get(answer, owner).isPresent());
    }

    @Test
    void guestLosesAccessAfterSessionTransferredEvenIfGuestIdRemains() {
        var guest = new Actor(null, UUID.randomUUID());
        long sid = session(null, guest.guestId());
        long mid = message(sid, "ASSISTANT", "ANSWER", "COMPLETED");
        feedback.save(mid, guest, like());
        jdbc.update("UPDATE chat_sessions SET user_id=1 WHERE session_id=?", sid);
        assertThrows(FeedbackStore.TargetUnavailable.class, () -> feedback.get(mid, guest));
        assertThrows(
                FeedbackStore.TargetUnavailable.class, () -> feedback.save(mid, guest, like()));
    }

    @Test
    void clarificationUserAndIncompleteMessagesCannotReceiveFeedback() {
        for (var values :
                List.of(
                        new String[] {"ASSISTANT", "CLARIFICATION", "COMPLETED"},
                        new String[] {"USER", "QUESTION", "COMPLETED"},
                        new String[] {"ASSISTANT", "ANSWER", "GENERATING"})) {
            long mid = message(session, values[0], values[1], values[2]);
            assertThrows(
                    FeedbackStore.TargetNotReady.class, () -> feedback.save(mid, owner, like()));
        }
    }

    @Test
    void guestCanCreateUpdateReadAndCancelOwnFeedback() {
        var guest = new Actor(null, UUID.randomUUID());
        long sid = session(null, guest.guestId());
        long mid = message(sid, "ASSISTANT", "ANSWER", "COMPLETED");
        var first = feedback.save(mid, guest, like());
        var changed =
                feedback.save(
                        mid, guest, new Input(Rating.DISLIKE, Reason.WRONG_INFO, "매장 정보 확인이 필요해요"));
        assertEquals(first.id(), changed.id());
        assertEquals(Rating.DISLIKE, feedback.get(mid, guest).orElseThrow().input().rating());
        assertThrows(FeedbackStore.TargetUnavailable.class, () -> feedback.get(mid, owner));
        feedback.cancel(mid, guest);
        assertTrue(feedback.get(mid, guest).isEmpty());
    }

    @Test
    void absentFeedbackCanBeReadAndCancelledWithoutCreatingRow() {
        assertTrue(feedback.get(answer, owner).isEmpty());
        feedback.cancel(answer, owner);
        assertEquals(
                0, jdbc.queryForObject("SELECT count(*) FROM message_feedback", Integer.class));
    }

    @Test
    void concurrentFeedbackCreatesOneRow() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var go = new CountDownLatch(1);
            Callable<Long> task =
                    () -> {
                        ready.countDown();
                        go.await();
                        return feedback.save(answer, owner, like()).id();
                    };
            var a = pool.submit(task);
            var b = pool.submit(task);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
            assertEquals(
                    1, jdbc.queryForObject("SELECT count(*) FROM message_feedback", Integer.class));
        }
    }

    @Test
    void readingFeedbackDoesNotLockChatRows() {
        feedback.save(answer, owner, like());
        try (var pool = Executors.newSingleThreadExecutor()) {
            transaction.executeWithoutResult(
                    status -> {
                        assertTrue(feedback.get(answer, owner).isPresent());
                        var otherConnection =
                                pool.submit(
                                        () ->
                                                jdbc.queryForObject(
                                                        "SELECT m.message_id FROM chat_messages m"
                                                            + " JOIN chat_sessions s ON"
                                                            + " s.session_id=m.session_id WHERE"
                                                            + " m.message_id=? FOR UPDATE OF s,m"
                                                            + " NOWAIT",
                                                        Long.class,
                                                        answer));
                        try {
                            assertEquals(answer, otherConnection.get(3, TimeUnit.SECONDS));
                        } catch (Exception failure) {
                            throw new AssertionError("조회가 채팅 행을 잠그면 안 됩니다.", failure);
                        }
                    });
        }
    }
}
