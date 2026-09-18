package com.telme.chat.repository;

import com.telme.chat.entity.ChatExecution;
import jakarta.persistence.LockModeType;
import java.util.Optional;
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
}
