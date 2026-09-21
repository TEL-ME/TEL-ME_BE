package com.telme.chat.repository;

import com.telme.chat.entity.ChatMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

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
              and message.messageType <> :excludedType
            order by message.sequenceNo desc
            """)
    List<ChatMessage> findCompletedContextMessagesBefore(
            @Param("sessionId") Long sessionId,
            @Param("afterSequenceNo") Integer afterSequenceNo,
            @Param("beforeSequenceNo") Integer beforeSequenceNo,
            @Param("completedStatus") ChatMessage.Status completedStatus,
            @Param("excludedType") ChatMessage.MessageType excludedType,
            Pageable pageable
    );

    @Query("""
            select message
            from ChatMessage message
            where message.session.sessionId = :sessionId
              and message.sequenceNo > :afterSequenceNo
              and message.sequenceNo <= :throughSequenceNo
              and message.status = :completedStatus
              and message.messageType <> :excludedType
            order by message.sequenceNo asc
            """)
    List<ChatMessage> findOldestCompletedSummaryMessagesAfter(
            @Param("sessionId") Long sessionId,
            @Param("afterSequenceNo") Integer afterSequenceNo,
            @Param("throughSequenceNo") Integer throughSequenceNo,
            @Param("completedStatus") ChatMessage.Status completedStatus,
            @Param("excludedType") ChatMessage.MessageType excludedType,
            Pageable pageable
    );
}
