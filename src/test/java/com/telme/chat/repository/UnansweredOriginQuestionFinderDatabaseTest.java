package com.telme.chat.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 로컬 DB 안의 무작위 전용 스키마만 생성·삭제한다. public 테이블은 쓰지 않는다. */
@EnabledIfEnvironmentVariable(named = "TELME_DB_TESTS", matches = "true")
class UnansweredOriginQuestionFinderDatabaseTest {

    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private UnansweredOriginQuestionFinder finder;
    private String schema;
    private long session;

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
        schema = "origin_question_test_" + UUID.randomUUID().toString().replace("-", "");
        admin.execute("CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public");
        admin.execute("CREATE SCHEMA " + schema);
        jdbc = new JdbcTemplate(
                new DriverManagerDataSource(url + "?currentSchema=" + schema + ",public", user, password));
        try (var stream = getClass().getResourceAsStream("/consult-fixtures/init-schema.sql")) {
            jdbc.execute(new String(
                    Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8));
        }
        jdbc.execute("INSERT INTO users(user_id,name) VALUES (1,'test owner')");
        finder = new UnansweredOriginQuestionFinder(jdbc);
        session = session();
    }

    @AfterEach
    void cleanup() {
        if (admin != null && schema != null) {
            admin.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void 되묻기_뒤_답변은_상담을_시작한_원래_질문을_잇는다() {
        long origin = message(session, "USER", "유심 재발급 가능한 매장을 알려주세요.");
        request(session, origin);
        message(session, "ASSISTANT", "어느 지역에서 찾으시나요?");
        message(session, "USER", "서울 강남구");
        long answer = message(session, "ASSISTANT", "안내드릴 수 있는 정보가 없습니다.");

        assertEquals(
                "유심 재발급 가능한 매장을 알려주세요.",
                finder.findByAnswerIds(List.of(answer)).get(answer));
    }

    @Test
    void 상담_요청이_여러_번이면_답변_직전_요청의_질문을_쓴다() {
        long first = message(session, "USER", "유심 재발급 어디서 하나요");
        request(session, first);
        message(session, "ASSISTANT", "어느 지역에서 찾으시나요?");
        long second = message(session, "USER", "요금제를 바꾸고 싶어요");
        request(session, second);
        long answer = message(session, "ASSISTANT", "안내드릴 수 있는 정보가 없습니다.");
        // 답변보다 뒤에 생긴 요청은 이 답변과 무관하다
        request(session, message(session, "USER", "해지 방법 알려주세요"));

        assertEquals("요금제를 바꾸고 싶어요", finder.findByAnswerIds(List.of(answer)).get(answer));
    }

    @Test
    void 다른_세션의_요청과_상담_없는_답변은_비어_있다() {
        long other = session();
        request(other, message(other, "USER", "다른 세션 질문"));
        long answer = message(session, "ASSISTANT", "안내드릴 수 있는 정보가 없습니다.");

        assertNull(finder.findByAnswerIds(List.of(answer)).get(answer));
        assertTrue(finder.findByAnswerIds(List.of()).isEmpty());
    }

    private long session() {
        return jdbc.queryForObject(
                "INSERT INTO chat_sessions(user_id,guest_id) VALUES (1,null) RETURNING session_id",
                Long.class);
    }

    private long message(long sid, String role, String content) {
        return jdbc.queryForObject(
                "INSERT INTO chat_messages(session_id,sequence_no,role,message_type,status,content)"
                        + " VALUES (?,(SELECT coalesce(max(sequence_no),0)+1 FROM chat_messages WHERE"
                        + " session_id=?),?,'QUESTION','COMPLETED',?) RETURNING message_id",
                Long.class, sid, sid, role, content);
    }

    private void request(long sid, long origin) {
        jdbc.update(
                "INSERT INTO consult_requests(session_id,origin_message_id,subquery_order,intent,query_text)"
                        + " VALUES (?,?,0,'STORE','nearby')",
                sid, origin);
    }
}
