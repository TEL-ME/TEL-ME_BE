package com.telme.chat.repository;

import com.telme.chat.entity.ChatMessage;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    boolean existsByMessageIdAndSession_UserId(Long messageId, Long userId);

    boolean existsByMessageIdAndSession_UserIdIsNullAndSession_GuestId(Long messageId, UUID guestId);

    @Query("""
            select coalesce(max(message.sequenceNo), 0)
            from ChatMessage message
            where message.session.sessionId = :sessionId
            """)
    int findMaxSequenceNo(@Param("sessionId") Long sessionId);

    @Query("""
            select message
            from ChatMessage message
            where message.session.sessionId = :sessionId
            order by message.sequenceNo desc
            """)
    List<ChatMessage> findLatestMessages(
            @Param("sessionId") Long sessionId,
            Pageable pageable
    );

    @Query("""
            select message
            from ChatMessage message
            where message.session.sessionId = :sessionId
              and message.sequenceNo < :beforeSequenceNo
            order by message.sequenceNo desc
            """)
    List<ChatMessage> findMessagesBefore(
            @Param("sessionId") Long sessionId,
            @Param("beforeSequenceNo") Integer beforeSequenceNo,
            Pageable pageable
    );

    @Query("""
            select message
            from ChatMessage message
            where message.session.sessionId = :sessionId
              and message.sequenceNo > :afterSequenceNo
              and message.sequenceNo < :beforeSequenceNo
              and message.status = :completedStatus
              and message.messageType not in :excludedTypes
            order by message.sequenceNo desc
            """)
    List<ChatMessage> findCompletedContextMessagesBefore(
            @Param("sessionId") Long sessionId,
            @Param("afterSequenceNo") Integer afterSequenceNo,
            @Param("beforeSequenceNo") Integer beforeSequenceNo,
            @Param("completedStatus") ChatMessage.Status completedStatus,
            @Param("excludedTypes") Collection<ChatMessage.MessageType> excludedTypes,
            Pageable pageable
    );

    @Query("""
            select message
            from ChatMessage message
            where message.session.sessionId = :sessionId
              and message.sequenceNo > :afterSequenceNo
              and message.sequenceNo <= :throughSequenceNo
              and message.status = :completedStatus
              and message.messageType not in :excludedTypes
            order by message.sequenceNo asc
            """)
    List<ChatMessage> findOldestCompletedSummaryMessagesAfter(
            @Param("sessionId") Long sessionId,
            @Param("afterSequenceNo") Integer afterSequenceNo,
            @Param("throughSequenceNo") Integer throughSequenceNo,
            @Param("completedStatus") ChatMessage.Status completedStatus,
            @Param("excludedTypes") Collection<ChatMessage.MessageType> excludedTypes,
            Pageable pageable
    );

    @Query("""
            select message
            from ChatMessage message
            join fetch message.session
            where message.messageId = :messageId
            """)
    Optional<ChatMessage> findByIdWithSession(@Param("messageId") Long messageId);

    // created_at이 DB 시각으로 채워지므로 "오늘"도 DB 시각으로 자른다(앱 서버 시계와 어긋나지 않게)
    // 서버 쪽 실패(FAILED)나 취소(CANCELLED)로 답을 받지 못한 질문은 세지 않는다. 처리 중(RUNNING)인 질문은 센다
    @Query(value = """
            select count(*)
            from chat_messages message
            join chat_sessions session on session.session_id = message.session_id
            where session.guest_id = :guestId
              and session.user_id is null
              and message.role = 'USER'
              and message.message_type = 'QUESTION'
              and message.created_at >= date_trunc('day', now() at time zone 'Asia/Seoul') at time zone 'Asia/Seoul'
              and not exists (
                  select 1 from chat_executions execution
                  where execution.input_message_id = message.message_id
                    and execution.status in ('FAILED', 'CANCELLED'))
            """, nativeQuery = true)
    long countGuestQuestionsToday(@Param("guestId") UUID guestId);
}
