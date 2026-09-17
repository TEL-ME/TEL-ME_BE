package com.telme.chat.repository;

import com.telme.chat.entity.ChatMessage;
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
}
