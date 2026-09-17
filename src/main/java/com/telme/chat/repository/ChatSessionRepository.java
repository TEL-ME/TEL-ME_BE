package com.telme.chat.repository;

import com.telme.chat.entity.ChatSession;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from ChatSession session where session.sessionId = :sessionId")
    Optional<ChatSession> findByIdForUpdate(@Param("sessionId") Long sessionId);

    @Query("""
            select session
            from ChatSession session
            where session.userId = :userId
            order by session.lastActiveAt desc, session.sessionId desc
            """)
    List<ChatSession> findFirstMemberSessions(
            @Param("userId") Long userId,
            Pageable pageable
    );

    @Query("""
            select session
            from ChatSession session
            where session.userId = :userId
              and (session.lastActiveAt < :cursorTime
                   or (session.lastActiveAt = :cursorTime and session.sessionId < :cursorId))
            order by session.lastActiveAt desc, session.sessionId desc
            """)
    List<ChatSession> findMemberSessionsBefore(
            @Param("userId") Long userId,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );

    @Query("""
            select session
            from ChatSession session
            where session.userId is null
              and session.guestId = :guestId
            order by session.lastActiveAt desc, session.sessionId desc
            """)
    List<ChatSession> findFirstGuestSessions(
            @Param("guestId") UUID guestId,
            Pageable pageable
    );

    @Query("""
            select session
            from ChatSession session
            where session.userId is null
              and session.guestId = :guestId
              and (session.lastActiveAt < :cursorTime
                   or (session.lastActiveAt = :cursorTime and session.sessionId < :cursorId))
            order by session.lastActiveAt desc, session.sessionId desc
            """)
    List<ChatSession> findGuestSessionsBefore(
            @Param("guestId") UUID guestId,
            @Param("cursorTime") Instant cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable
    );
}
