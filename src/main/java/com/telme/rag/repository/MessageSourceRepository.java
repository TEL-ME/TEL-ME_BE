package com.telme.rag.repository;

import com.telme.rag.entity.MessageSource;
import java.util.Collection;
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

    // FAQ 목록을 먼저 뽑고 횟수만 따로 센다. 조인으로 합치면 인용 0건인 FAQ가 목록에서 사라진다
    @Query("select new com.telme.rag.repository.FaqCitationCount(source.faqId, count(source)) "
            + "from MessageSource source where source.faqId in :faqIds group by source.faqId")
    List<FaqCitationCount> countByFaqIds(@Param("faqIds") Collection<Long> faqIds);
}
