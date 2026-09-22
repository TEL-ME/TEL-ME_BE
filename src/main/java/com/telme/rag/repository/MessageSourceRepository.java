package com.telme.rag.repository;

import com.telme.rag.entity.MessageSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageSourceRepository extends JpaRepository<MessageSource, Long> {

    // 파생 delete는 엔티티를 지우는 방식이라 flush 시 INSERT가 DELETE보다 먼저 나간다.
    // 지우고 다시 넣는 순서를 보장하려고 벌크 delete를 쓴다
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MessageSource source where source.message.messageId = :messageId")
    void deleteByMessageId(@Param("messageId") Long messageId);
}
