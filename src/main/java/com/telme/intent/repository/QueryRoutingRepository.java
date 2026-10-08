package com.telme.intent.repository;

import com.telme.intent.entity.QueryRouting;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QueryRoutingRepository extends JpaRepository<QueryRouting, Long> {

    Optional<QueryRouting> findByMessage_MessageId(Long messageId);

    // 하위 상담과 조인하지 않아 복합 질문도 분류 기록 한 건으로 센다.
    @Query("""
            select new com.telme.intent.repository.RoutingCount(r.intent, r.method, count(r))
            from QueryRouting r
            where r.createdAt >= :from and r.createdAt < :to
            group by r.intent, r.method
            """)
    List<RoutingCount> countDistribution(@Param("from") Instant from, @Param("to") Instant to);

    @Query("""
            select new com.telme.intent.repository.RoutingCount(r.intent, r.method, count(r))
            from QueryRouting r
            group by r.intent, r.method
            """)
    List<RoutingCount> countDistributionAll();
}
