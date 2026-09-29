package com.telme.faq.repository;

import com.telme.faq.entity.Faq;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FaqRepository extends JpaRepository<Faq, Long> {

    // 조건을 안 준 항목은 "%"로 넘어온다. 검색어 안의 %와 _는 호출부에서 역슬래시로 막아 보낸다
    String ADMIN_FILTER = """
            where (lower(faq.question) like :keyword escape '\\'
                    or lower(faq.answer) like :keyword escape '\\')
              and faq.category like :category escape '\\'
              and faq.status in :statuses
            """;

    // group by가 들어간 쿼리는 count 쿼리가 유도되지 않아 직접 준다
    String ADMIN_COUNT = "select count(faq) from Faq faq " + ADMIN_FILTER;

    String ADMIN_CITATION_QUERY = "select faq.faqId from Faq faq "
            + "left join MessageSource source on source.faqId = faq.faqId " + ADMIN_FILTER
            + " group by faq.faqId ";

    // 배치 적재에서 이미 들어간 건을 건너뛸 때 사용
    List<Faq> findByContentHashIn(Collection<String> contentHashes);

    @Query(value = "select faq.faqId from Faq faq " + ADMIN_FILTER
            + " order by faq.updatedAt desc, faq.faqId desc",
            countQuery = ADMIN_COUNT)
    Page<Long> findAdminFaqIds(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("statuses") Collection<Faq.Status> statuses,
            Pageable pageable);

    // 인용 0건인 FAQ가 빠지지 않도록 inner join이 아니라 left join으로 센다. 목록에서 가장 먼저 봐야 할 값이다
    @Query(value = ADMIN_CITATION_QUERY + "order by count(source) asc, faq.faqId asc",
            countQuery = ADMIN_COUNT)
    Page<Long> findAdminFaqIdsByCitationAsc(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("statuses") Collection<Faq.Status> statuses,
            Pageable pageable);

    @Query(value = ADMIN_CITATION_QUERY + "order by count(source) desc, faq.faqId asc",
            countQuery = ADMIN_COUNT)
    Page<Long> findAdminFaqIdsByCitationDesc(
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("statuses") Collection<Faq.Status> statuses,
            Pageable pageable);
}
