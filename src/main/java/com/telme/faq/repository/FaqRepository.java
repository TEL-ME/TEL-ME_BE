package com.telme.faq.repository;

import com.telme.faq.entity.Faq;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    // 배치 적재에서 이미 들어간 slot을 건너뛸 때 사용
    List<Faq> findBySlotIdIn(Collection<String> slotIds);

    // 배치 적재에서 slot_id 도입 전에 들어간 행을 찾아 slot_id를 채울 때 사용
    List<Faq> findByContentHashInAndSlotIdIsNullOrderByFaqIdAsc(Collection<String> contentHashes);
}
