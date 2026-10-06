package com.telme.chat.repository;

import com.telme.chat.entity.ChatMessage;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 관리자 조회 전용. 채팅 저장은 ChatMessageRepository가 맡는다
public interface AdminUnansweredRepository extends JpaRepository<ChatMessage, Long> {

    // ix_chat_messages_unanswered와 같은 조건이라 이 줄을 바꾸면 인덱스도 함께 봐야 한다
    String UNANSWERED = """
            m.role = com.telme.chat.entity.ChatMessage$Role.ASSISTANT
              and (m.answerBasis in (com.telme.chat.entity.ChatMessage$AnswerBasis.NO_EVIDENCE,
                                     com.telme.chat.entity.ChatMessage$AnswerBasis.OUT_OF_SCOPE)
                   or m.status in (com.telme.chat.entity.ChatMessage$Status.FAILED,
                                   com.telme.chat.entity.ChatMessage$Status.TIMEOUT))
            """;

    String UNANSWERED_WHERE = " where " + UNANSWERED
            + " and m.createdAt >= :from and m.createdAt < :to ";

    String EITHER = " and (m.answerBasis in :bases or m.status in :statuses)";

    String ORDER = " order by m.createdAt desc, m.messageId desc";

    // 질문은 앞 메시지에 있다. 한 줄씩 다시 읽지 않도록 함께 가져온다.
    // 되묻기 없이 시작한 답변은 앞 메시지가 없을 수 있어 left join
    String SELECT = "select m from ChatMessage m left join fetch m.replyTo ";
    String COUNT = "select count(m) from ChatMessage m ";

    // 고른 유형이 어느 컬럼에 걸리느냐로 나눈다. 빈 목록을 in에 넘기면 쿼리가 깨져 비어 있는 쪽은 부르지 않는다
    @Query(value = SELECT + UNANSWERED_WHERE + ORDER, countQuery = COUNT + UNANSWERED_WHERE)
    Page<ChatMessage> findUnanswered(
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query(value = SELECT + UNANSWERED_WHERE + " and m.answerBasis in :bases" + ORDER,
            countQuery = COUNT + UNANSWERED_WHERE + " and m.answerBasis in :bases")
    Page<ChatMessage> findUnansweredByBases(
            @Param("bases") Collection<ChatMessage.AnswerBasis> bases,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query(value = SELECT + UNANSWERED_WHERE + " and m.status in :statuses" + ORDER,
            countQuery = COUNT + UNANSWERED_WHERE + " and m.status in :statuses")
    Page<ChatMessage> findUnansweredByStatuses(
            @Param("statuses") Collection<ChatMessage.Status> statuses,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query(value = SELECT + UNANSWERED_WHERE + EITHER + ORDER,
            countQuery = COUNT + UNANSWERED_WHERE + EITHER)
    Page<ChatMessage> findUnansweredByBasesOrStatuses(
            @Param("bases") Collection<ChatMessage.AnswerBasis> bases,
            @Param("statuses") Collection<ChatMessage.Status> statuses,
            @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query(SELECT + " where m.messageId = :messageId and " + UNANSWERED)
    Optional<ChatMessage> findUnansweredById(@Param("messageId") Long messageId);

    @Query(COUNT + UNANSWERED_WHERE)
    long countUnanswered(@Param("from") Instant from, @Param("to") Instant to);

    @Query(COUNT + UNANSWERED_WHERE + " and m.status in :statuses")
    long countUnansweredByStatuses(@Param("statuses") Collection<ChatMessage.Status> statuses,
            @Param("from") Instant from, @Param("to") Instant to);
}
