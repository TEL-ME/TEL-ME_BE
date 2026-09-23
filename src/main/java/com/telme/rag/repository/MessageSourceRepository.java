package com.telme.rag.repository;

import com.telme.rag.entity.MessageSource;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageSourceRepository extends JpaRepository<MessageSource, Long> {

    List<MessageSource> findByMessage_MessageIdOrderBySearchRankAscSourceIdAsc(Long messageId);

    // 파생 delete는 flush 시 INSERT가 DELETE보다 먼저 나가서 지우고 넣는 순서가 보장되지 않음
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MessageSource source where source.message.messageId = :messageId")
    void deleteByMessageId(@Param("messageId") Long messageId);
}
