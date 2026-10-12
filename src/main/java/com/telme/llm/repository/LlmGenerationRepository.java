package com.telme.llm.repository;

import com.telme.global.common.DailyCount;
import com.telme.llm.entity.LlmGeneration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LlmGenerationRepository extends JpaRepository<LlmGeneration, Long> {
    
    // 같은 시각의 기록이 여러 건일 수 있어 id를 보조 정렬로 둔다.
    @Query("""
            select g from LlmGeneration g
            where g.status in (com.telme.llm.entity.LlmGeneration$Status.TIMEOUT,
                               com.telme.llm.entity.LlmGeneration$Status.CONNECTION_FAILED,
                               com.telme.llm.entity.LlmGeneration$Status.MODEL_ERROR)
              and g.status in :statuses
              and g.taskType in :taskTypes
              and g.createdAt >= :from and g.createdAt < :to
            order by g.createdAt desc, g.generationId desc
            """)
    Page<LlmGeneration> findAdminErrors(
            @Param("statuses") Collection<LlmGeneration.Status> statuses,
            @Param("taskTypes") Collection<LlmGeneration.TaskType> taskTypes,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
    
    // 관리자 오류 목록과 같은 기준으로 센다. 재시도마다 행이 남아 1회차 실패 후 회복된 시도도 1건이다.
    // 한국 시간 날짜로 묶고, 기록이 없는 날은 행이 없다
    @Query(value = """
            select cast(created_at at time zone 'Asia/Seoul' as date) as day, count(*) as count
            from llm_generations
            where status in ('TIMEOUT', 'CONNECTION_FAILED', 'MODEL_ERROR')
              and created_at >= :from and created_at < :to
            group by 1
            """, nativeQuery = true)
    List<DailyCount> countErrorsByDay(@Param("from") Instant from, @Param("to") Instant to);
}
