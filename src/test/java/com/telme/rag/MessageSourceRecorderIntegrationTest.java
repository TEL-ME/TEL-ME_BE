package com.telme.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.member.entity.User;
import com.telme.rag.dto.res.AnswerResult.AnswerSource;
import com.telme.rag.entity.MessageSource;
import com.telme.rag.repository.MessageSourceRepository;
import com.telme.rag.service.MessageSourceRecorder;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

// 별도 트랜잭션(REQUIRES_NEW) 동작을 확인해야 해서 @Transactional 없이 실행하고 데이터는 직접 지운다
@SpringBootTest
class MessageSourceRecorderIntegrationTest {

    @Autowired
    private MessageSourceRecorder messageSourceRecorder;

    @Autowired
    private MessageSourceRepository messageSourceRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long sessionId;
    private Long userId;

    @AfterEach
    void cleanUp() {
        if (sessionId != null) {
            jdbcTemplate.update("delete from message_sources where message_id in"
                    + " (select message_id from chat_messages where session_id = ?)", sessionId);
            jdbcTemplate.update("delete from chat_messages where session_id = ?", sessionId);
            jdbcTemplate.update("delete from chat_sessions where session_id = ?", sessionId);
        }
        if (userId != null) {
            jdbcTemplate.update("delete from users where user_id = ?", userId);
        }
    }

    @Test
    @DisplayName("답변 근거를 스냅샷 값 그대로 저장한다")
    void 근거를_저장한다() {
        Long messageId = persistAnswerMessage();

        messageSourceRecorder.record(messageId, List.of(source(1L, 1), source(2L, 2)));

        List<MessageSource> saved = savedSources(messageId);
        assertThat(saved).hasSize(2);

        MessageSource first = saved.getFirst();
        assertThat(first.getFaqId()).isEqualTo(1L);
        assertThat(first.getTitleSnapshot()).isEqualTo("질문1");
        assertThat(first.getFaqVersion()).isEqualTo(1);
        assertThat(first.getFaqUpdatedAt()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(first.getSearchRank()).isEqualTo((short) 1);
        assertThat(first.getScore()).isEqualByComparingTo(new BigDecimal("0.9123"));
    }

    @Test
    @DisplayName("근거가 없으면 아무것도 저장하지 않는다")
    void 근거가_없으면_저장하지_않는다() {
        Long messageId = persistAnswerMessage();

        messageSourceRecorder.record(messageId, List.of());

        assertThat(savedSources(messageId)).isEmpty();
    }

    @Test
    @DisplayName("같은 답변으로 다시 저장해도 근거가 쌓이지 않는다")
    void 다시_저장해도_쌓이지_않는다() {
        Long messageId = persistAnswerMessage();

        messageSourceRecorder.record(messageId, List.of(source(1L, 1)));
        messageSourceRecorder.record(messageId, List.of(source(1L, 1), source(2L, 2)));

        assertThat(savedSources(messageId)).hasSize(2);
    }

    @Test
    @DisplayName("제목이 길어도 잘라서 저장한다")
    void 긴_제목을_잘라_저장한다() {
        Long messageId = persistAnswerMessage();
        AnswerSource longTitle = AnswerSource.builder()
                .faqId(1L)
                .titleSnapshot("가".repeat(250))
                .faqVersion(1)
                .faqUpdatedAt(LocalDate.of(2026, 9, 17))
                .searchRank((short) 1)
                .score(new BigDecimal("0.9123"))
                .build();

        messageSourceRecorder.record(messageId, List.of(longTitle));

        List<MessageSource> saved = savedSources(messageId);
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getTitleSnapshot()).hasSize(200);
    }

    @Test
    @DisplayName("저장이 실패해도 호출한 쪽 트랜잭션은 계속 진행된다")
    void 저장_실패가_호출한_쪽을_막지_않는다() {
        Long missingMessageId = -1L;

        assertThatCode(() -> transactionTemplate.execute(status -> {
            messageSourceRecorder.record(missingMessageId, List.of(source(1L, 1)));
            return null;
        })).doesNotThrowAnyException();
    }

    private List<MessageSource> savedSources(Long messageId) {
        return messageSourceRepository.findAll().stream()
                .filter(source -> source.getMessage().getMessageId().equals(messageId))
                .toList();
    }

    private AnswerSource source(long faqId, int rank) {
        return AnswerSource.builder()
                .faqId(faqId)
                .titleSnapshot("질문" + faqId)
                .faqVersion(1)
                .faqUpdatedAt(LocalDate.of(2026, 9, 17))
                .searchRank((short) rank)
                .score(new BigDecimal("0.9123"))
                .build();
    }

    private Long persistAnswerMessage() {
        return transactionTemplate.execute(status -> {
            User user = User.builder()
                    .email("source-" + UUID.randomUUID() + "@example.com")
                    .name("source")
                    .build();
            entityManager.persist(user);
            userId = user.getUserId();

            ChatSession session = ChatSession.builder().userId(user.getUserId()).build();
            entityManager.persist(session);
            sessionId = session.getSessionId();

            ChatMessage message = ChatMessage.builder()
                    .session(session)
                    .sequenceNo(1)
                    .role(ChatMessage.Role.ASSISTANT)
                    .messageType(ChatMessage.MessageType.ANSWER)
                    .content("답변")
                    .build();
            entityManager.persist(message);
            entityManager.flush();
            return message.getMessageId();
        });
    }
}
