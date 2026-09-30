package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.chat.dto.req.AdminUnansweredSearchRequest;
import com.telme.chat.dto.req.AdminUnansweredType;
import com.telme.chat.dto.res.AdminUnansweredListItemResponse;
import com.telme.chat.dto.res.AdminUnansweredListResponse;
import com.telme.global.common.exception.GeneralException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// 확인용 세션과 메시지를 직접 넣어 조건을 본다. 트랜잭션 롤백으로 시드에 영향 없음
@SpringBootTest
@Transactional
class AdminUnansweredQueryServiceTest {

    @Autowired
    private AdminUnansweredQueryService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long sessionId;
    private Long noEvidenceId;
    private Long outOfScopeId;
    private Long timeoutId;
    private Long groundedId;

    @BeforeEach
    void setUp() {
        sessionId = jdbcTemplate.queryForObject(
                "INSERT INTO chat_sessions(user_id) VALUES (1) RETURNING session_id", Long.class);
        long question = message(1, "USER", "QUESTION", "COMPLETED", null, null, "요금제 바꾸고 싶어요");
        noEvidenceId = message(2, "ASSISTANT", "ANSWER", "COMPLETED", "NO_EVIDENCE", question, "안내드릴 수 있는 정보가 없습니다.");
        outOfScopeId = message(3, "ASSISTANT", "ANSWER", "COMPLETED", "OUT_OF_SCOPE", question, "답할 수 없습니다.");
        timeoutId = message(4, "ASSISTANT", "ERROR", "TIMEOUT", null, question, null);
        groundedId = message(5, "ASSISTANT", "ANSWER", "COMPLETED", "GROUNDED", question, "정상 답변");
    }

    @Test
    @DisplayName("조건 없이 조회하면 답 못 한 네 가지만 나오고 정상 답변은 빠진다")
    void 답_못_한_것만_본다() {
        List<Long> ids = ids(service.getUnanswered(request(null, null, null)));

        assertThat(ids).contains(noEvidenceId, outOfScopeId, timeoutId).doesNotContain(groundedId);
    }

    @Test
    @DisplayName("유형을 주면 그 유형만 남는다")
    void 유형으로_거른다() {
        assertThat(ids(service.getUnanswered(request(AdminUnansweredType.NO_EVIDENCE, null, null))))
                .contains(noEvidenceId)
                .doesNotContain(outOfScopeId, timeoutId);
        assertThat(ids(service.getUnanswered(request(AdminUnansweredType.TIMEOUT, null, null))))
                .contains(timeoutId)
                .doesNotContain(noEvidenceId, outOfScopeId);
    }

    @Test
    @DisplayName("기간을 주면 그 밖의 답변은 빠진다")
    void 기간으로_거른다() {
        Instant now = Instant.now();

        assertThat(ids(service.getUnanswered(request(null, now.minus(1, ChronoUnit.HOURS), null))))
                .contains(noEvidenceId);
        assertThat(ids(service.getUnanswered(request(null, null, now.minus(1, ChronoUnit.HOURS)))))
                .doesNotContain(noEvidenceId);
    }

    @Test
    @DisplayName("목록에 유형과 사용자가 물어본 질문이 함께 나온다")
    void 유형과_질문을_보여준다() {
        AdminUnansweredListResponse response = service.getUnanswered(request(null, null, null));

        AdminUnansweredListItemResponse item = response.messages().stream()
                .filter(each -> each.messageId().equals(timeoutId))
                .findFirst()
                .orElseThrow();
        assertThat(item.type()).isEqualTo("TIMEOUT");
        assertThat(item.questionPreview()).isEqualTo("요금제 바꾸고 싶어요");
        assertThat(item.sessionId()).isEqualTo(sessionId);
    }

    @Test
    @DisplayName("기간의 시작이 끝보다 늦으면 CHAT400-1을 던진다")
    void 뒤집힌_기간은_막는다() {
        Instant now = Instant.now();

        assertThatThrownBy(() -> service.getUnanswered(
                request(null, now, now.minus(1, ChronoUnit.DAYS))))
                .isInstanceOf(GeneralException.class);
    }

    private long message(
            int sequenceNo, String role, String type, String status, String basis, Long replyTo, String content) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO chat_messages(session_id, sequence_no, role, message_type, status,"
                        + " answer_basis, reply_to_id, content) VALUES (?,?,?,?,?,?,?,?)"
                        + " RETURNING message_id",
                Long.class, sessionId, sequenceNo, role, type, status, basis, replyTo, content);
    }

    private List<Long> ids(AdminUnansweredListResponse response) {
        return response.messages().stream().map(AdminUnansweredListItemResponse::messageId).toList();
    }

    private AdminUnansweredSearchRequest request(AdminUnansweredType type, Instant from, Instant to) {
        return new AdminUnansweredSearchRequest(type, from, to, null, 100);
    }
}
