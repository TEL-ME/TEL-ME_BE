package com.telme.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telme.chat.dto.req.AdminUnansweredSearchRequest;
import com.telme.chat.dto.req.AdminUnansweredType;
import com.telme.chat.dto.res.AdminUnansweredDetailResponse;
import com.telme.chat.entity.ChatMessage;
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
    private Long failedId;
    private Long groundedId;

    @BeforeEach
    void setUp() {
        sessionId = jdbcTemplate.queryForObject(
                "INSERT INTO chat_sessions(user_id) VALUES (1) RETURNING session_id", Long.class);
        long question = message(1, "USER", "QUESTION", "COMPLETED", null, null, "요금제 바꾸고 싶어요");
        noEvidenceId = message(2, "ASSISTANT", "ANSWER", "COMPLETED", "NO_EVIDENCE", question, "안내드릴 수 있는 정보가 없습니다.");
        outOfScopeId = message(3, "ASSISTANT", "ANSWER", "COMPLETED", "OUT_OF_SCOPE", question, "답할 수 없습니다.");
        timeoutId = message(4, "ASSISTANT", "ERROR", "TIMEOUT", null, question, null);
        failedId = message(5, "ASSISTANT", "ERROR", "FAILED", null, question, null);
        groundedId = message(6, "ASSISTANT", "ANSWER", "COMPLETED", "GROUNDED", question, "정상 답변");
    }

    @Test
    @DisplayName("조건 없이 조회하면 답 못 한 네 가지만 나오고 정상 답변은 빠진다")
    void 답_못_한_것만_본다() {
        List<Long> ids = ids(service.getUnanswered(request(null, null, null)));

        assertThat(ids).contains(noEvidenceId, outOfScopeId, timeoutId, failedId).doesNotContain(groundedId);
    }

    @Test
    @DisplayName("유형을 주면 그 유형만 남는다")
    void 유형으로_거른다() {
        assertThat(ids(service.getUnanswered(request(AdminUnansweredType.NO_EVIDENCE, null, null))))
                .contains(noEvidenceId)
                .doesNotContain(outOfScopeId, timeoutId);
        assertThat(ids(service.getUnanswered(request(AdminUnansweredType.TIMEOUT, null, null))))
                .contains(timeoutId)
                .doesNotContain(noEvidenceId, outOfScopeId, failedId);
        assertThat(ids(service.getUnanswered(request(AdminUnansweredType.FAILED, null, null))))
                .contains(failedId)
                .doesNotContain(noEvidenceId, outOfScopeId, timeoutId);
        assertThat(ids(service.getUnanswered(request(AdminUnansweredType.OUT_OF_SCOPE, null, null))))
                .contains(outOfScopeId)
                .doesNotContain(noEvidenceId, timeoutId, failedId);
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

    @Test
    @DisplayName("상세에는 질문·응답과 답을 만들 때 뽑힌 FAQ가 함께 나온다")
    void 상세를_본다() {
        // 뽑힌 FAQ는 있었지만 근거로 답하지 못한 경우
        jdbcTemplate.update(
                "INSERT INTO message_sources(message_id, faq_id, title_snapshot, faq_version, search_rank, score)"
                        + " VALUES (?, 1, '요금제는 언제 변경할 수 있나요?', 1, 1, 0.5123)",
                noEvidenceId);

        AdminUnansweredDetailResponse detail = service.getUnanswered(noEvidenceId);

        assertThat(detail.type()).isEqualTo("NO_EVIDENCE");
        assertThat(detail.question()).isEqualTo("요금제 바꾸고 싶어요");
        assertThat(detail.answer()).isEqualTo("안내드릴 수 있는 정보가 없습니다.");
        assertThat(detail.sessionId()).isEqualTo(sessionId);
        assertThat(detail.sources()).singleElement()
                .satisfies(source -> {
                    assertThat(source.faqId()).isEqualTo(1L);
                    assertThat(source.searchRank()).isEqualTo((short) 1);
                });
    }

    @Test
    @DisplayName("뽑힌 FAQ가 없으면 빈 목록으로 나온다")
    void 근거가_없으면_빈_목록이다() {
        AdminUnansweredDetailResponse detail = service.getUnanswered(timeoutId);

        assertThat(detail.type()).isEqualTo("TIMEOUT");
        assertThat(detail.sources()).isEmpty();
    }

    @Test
    @DisplayName("답한 메시지를 상세로 부르면 CHAT404-2를 던진다")
    void 답한_메시지는_상세에서_빠진다() {
        assertThatThrownBy(() -> service.getUnanswered(groundedId))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("없는 메시지를 상세로 부르면 CHAT404-2를 던진다")
    void 없는_메시지는_막는다() {
        assertThatThrownBy(() -> service.getUnanswered(-1L)).isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("유형 이름은 엔티티의 answer_basis·status 값과 같다")
    void 유형_이름이_엔티티와_맞는다() {
        // 조회가 type.name()으로 엔티티 값을 찾는다. 이름이 어긋나면 그 유형 조회가 500이 된다
        assertThatCode(() -> {
            for (AdminUnansweredType type : AdminUnansweredType.values()) {
                if (type.isBasis()) {
                    ChatMessage.AnswerBasis.valueOf(type.name());
                } else {
                    ChatMessage.Status.valueOf(type.name());
                }
            }
        }).doesNotThrowAnyException();
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
