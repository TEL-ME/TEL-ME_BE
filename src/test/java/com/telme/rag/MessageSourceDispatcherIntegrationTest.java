package com.telme.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.telme.chat.entity.ChatMessage;
import com.telme.chat.entity.ChatSession;
import com.telme.member.entity.User;
import com.telme.rag.dto.res.AnswerResult.AnswerSource;
import com.telme.rag.entity.MessageSource;
import com.telme.rag.repository.MessageSourceRepository;
import com.telme.rag.service.AnswerSourcesReady;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

// 커밋 후 별도 스레드 저장을 확인해야 해서 @Transactional 없이 실행하고 데이터는 직접 지운다
@SpringBootTest
class MessageSourceDispatcherIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

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
    @DisplayName("답변 메시지를 저장한 트랜잭션 안에서 발행해도 근거가 저장된다")
    void 커밋_전_발행도_저장된다() {
        Long messageId = publishInsideMessageTransaction(List.of(source(1L, 1), source(2L, 2)));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(savedSources(messageId)).hasSize(2));

        MessageSource first = savedSources(messageId).getFirst();
        assertThat(first.getFaqId()).isEqualTo(1L);
        assertThat(first.getTitleSnapshot()).isEqualTo("질문1");
        assertThat(first.getFaqVersion()).isEqualTo(1);
        assertThat(first.getFaqUpdatedAt()).isEqualTo(LocalDate.of(2026, 9, 17));
        assertThat(first.getSearchRank()).isEqualTo((short) 1);
        assertThat(first.getScore()).isEqualByComparingTo(new BigDecimal("0.9123"));
    }

    @Test
    @DisplayName("커밋이 늦어져도 커밋 후에 근거를 저장한다")
    void 커밋을_기다렸다_저장한다() {
        Long messageId = publishInsideMessageTransaction(
                List.of(source(1L, 1), source(2L, 2)), Duration.ofMillis(500));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(savedSources(messageId)).hasSize(2));
    }

    @Test
    @DisplayName("답변 메시지 저장이 롤백되면 근거도 저장하지 않는다")
    void 롤백되면_저장하지_않는다() {
        Long[] messageId = new Long[1];

        try {
            transactionTemplate.executeWithoutResult(status -> {
                messageId[0] = persistAnswerMessage();
                eventPublisher.publishEvent(new AnswerSourcesReady(messageId[0], List.of(source(1L, 1))));
                throw new IllegalStateException("답변 저장 실패");
            });
        } catch (IllegalStateException ignored) {
            // 롤백 유도
        }

        assertThat(countSources(messageId[0])).isZero();
    }

    @Test
    @DisplayName("근거가 없으면 아무것도 저장하지 않는다")
    void 근거가_없으면_저장하지_않는다() {
        Long messageId = publishInsideMessageTransaction(List.of());

        assertThat(countSources(messageId)).isZero();
    }

    @Test
    @DisplayName("같은 답변으로 다시 저장해도 근거가 쌓이지 않는다")
    void 다시_저장해도_쌓이지_않는다() {
        Long messageId = publishInsideMessageTransaction(List.of(source(1L, 1)));
        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(savedSources(messageId)).hasSize(1));

        publishAfterCommit(messageId, List.of(source(1L, 1), source(2L, 2)));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(savedSources(messageId)).hasSize(2));
    }

    @Test
    @DisplayName("제목이 길어도 잘라서 저장한다")
    void 긴_제목을_잘라_저장한다() {
        AnswerSource longTitle = AnswerSource.builder()
                .faqId(1L)
                .titleSnapshot("가".repeat(250))
                .faqVersion(1)
                .faqUpdatedAt(LocalDate.of(2026, 9, 17))
                .searchRank((short) 1)
                .score(new BigDecimal("0.9123"))
                .build();

        Long messageId = publishInsideMessageTransaction(List.of(longTitle));

        await().atMost(TIMEOUT).untilAsserted(() -> {
            List<MessageSource> saved = savedSources(messageId);
            assertThat(saved).hasSize(1);
            assertThat(saved.getFirst().getTitleSnapshot()).hasSize(200);
        });
    }

    @Test
    @DisplayName("답변 메시지가 없어도 다른 작업을 막지 않는다")
    void 저장_실패가_다른_작업을_막지_않는다() {
        Long missingMessageId = -1L;

        publishAfterCommit(missingMessageId, List.of(source(1L, 1)));

        assertThat(countSources(missingMessageId)).isZero();
    }

    @Test
    @DisplayName("트랜잭션 밖에서 발행해도 근거를 저장한다")
    void 트랜잭션_밖_발행도_저장한다() {
        Long messageId = transactionTemplate.execute(status -> persistAnswerMessage());

        // 파이프라인은 답변 저장이 끝난 뒤 트랜잭션 없이 발행한다
        eventPublisher.publishEvent(new AnswerSourcesReady(messageId, List.of(source(1L, 1))));

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(savedSources(messageId)).hasSize(1));
    }

    // 답변 메시지를 INSERT한 트랜잭션 안에서 발행한다. 실제 호출부와 같은 순서
    private Long publishInsideMessageTransaction(List<AnswerSource> sources) {
        return publishInsideMessageTransaction(sources, Duration.ZERO);
    }

    // holdOpen만큼 커밋을 늦춘다. 커밋 전에 저장을 시도하면 FK 위반으로 한 건도 남지 않는다
    private Long publishInsideMessageTransaction(List<AnswerSource> sources, Duration holdOpen) {
        return transactionTemplate.execute(status -> {
            Long messageId = persistAnswerMessage();
            eventPublisher.publishEvent(new AnswerSourcesReady(messageId, sources));
            sleep(holdOpen);
            return messageId;
        });
    }

    private void sleep(Duration duration) {
        if (duration.isZero()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void publishAfterCommit(Long messageId, List<AnswerSource> sources) {
        transactionTemplate.executeWithoutResult(status ->
                eventPublisher.publishEvent(new AnswerSourcesReady(messageId, sources)));
    }

    private List<MessageSource> savedSources(Long messageId) {
        return messageSourceRepository.findAll().stream()
                .filter(source -> source.getMessage().getMessageId().equals(messageId))
                .toList();
    }

    private int countSources(Long messageId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from message_sources where message_id = ?", Integer.class, messageId);
        return count == null ? 0 : count;
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
    }
}
