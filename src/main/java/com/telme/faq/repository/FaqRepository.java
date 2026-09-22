package com.telme.faq.repository;

import com.telme.faq.entity.Faq;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    // 배치 적재에서 이미 들어간 건을 건너뛸 때 사용
    List<Faq> findByContentHashIn(Collection<String> contentHashes);
}
