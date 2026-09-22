package com.telme.rag.repository;

import com.telme.rag.entity.MessageSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageSourceRepository extends JpaRepository<MessageSource, Long> {

    void deleteByMessage_MessageId(Long messageId);
}
