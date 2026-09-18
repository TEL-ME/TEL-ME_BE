package com.telme.chat.repository;

import com.telme.chat.entity.ChatExecution;
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

public interface ChatExecutionRepository extends JpaRepository<ChatExecution, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select execution
            from ChatExecution execution
            where execution.executionId = :executionId
            """)
    Optional<ChatExecution> findExecutionByIdForUpdate(@Param("executionId") Long executionId);

    Optional<ChatExecution> findFirstBySession_SessionIdAndStatusOrderByStartedAtDesc(
            Long sessionId,
            ChatExecution.Status status
    );

    @Query("""
            select execution.executionId
            from ChatExecution execution
            where execution.status = :status
              and execution.startedAt <= :startedBefore
              and execution.executionId > :afterId
            order by execution.executionId
            """)
    List<Long> findExecutionIdsStartedBefore(
            @Param("status") ChatExecution.Status status,
            @Param("startedBefore") Instant startedBefore,
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    @Query("""
            select execution
            from ChatExecution execution
            where execution.executionId = :executionId
              and execution.session.userId = :userId
            """)
    Optional<ChatExecution> findMemberExecution(
            @Param("executionId") Long executionId,
            @Param("userId") Long userId
    );

    @Query("""
            select execution
            from ChatExecution execution
            where execution.executionId = :executionId
              and execution.session.userId is null
              and execution.session.guestId = :guestId
            """)
    Optional<ChatExecution> findGuestExecution(
            @Param("executionId") Long executionId,
            @Param("guestId") UUID guestId
    );
}
