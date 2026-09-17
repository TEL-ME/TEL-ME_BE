package com.telme.chat.repository;

import com.telme.chat.entity.ChatExecution;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatExecutionRepository extends JpaRepository<ChatExecution, Long> {
}
