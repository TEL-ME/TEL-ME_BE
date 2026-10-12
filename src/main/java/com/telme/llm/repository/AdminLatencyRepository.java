package com.telme.llm.repository;

import com.telme.llm.entity.LlmGeneration;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

//관리자 응답 속도 집계 전용. 백분위수(percentile_cont)는 JPQL에 없어 네이티브 쿼리로 쓴다
public interface AdminLatencyRepository extends JpaRepository<LlmGeneration, Long> {

    interface StatsView {
        long getCount();
        Double getAvgMs();
        Double getP50Ms();
        Double getP95Ms();
    }
    
    interface TaskStatsView extends StatsView {
        String getTaskType();
    }
    
    @Query(value = """
            select count(*) as count,
                   avg(ms) as avgMs,
                   percentile_cont(0.5) within group (order by ms) as p50Ms,
                   percentile_cont(0.95) within group (order by ms) as p95Ms
            from (select extract(epoch from (ended_at - started_at)) * 1000 as ms  from chat_executions
            where status = 'COMPLETED' and ended_at is not null and output_message_id is not null
                    and started_at >= :from and started_at < :to) e
            """, nativeQuery = true)
    StatsView findExecutionStats(@Param("from") Instant from, @Param("to") Instant to);
    
    @Query(value = """
            select count(*) as count,
                   avg(first_token_ms) as avgMs,
                   percentile_cont(0.5) within group (order by first_token_ms) as p50Ms,
                   percentile_cont(0.95) within group (order by first_token_ms) as p95Ms
            from llm_generations
            where status = 'SUCCESS' and first_token_ms is not null
                and created_at >= :from and created_at < :to
            """, nativeQuery = true)
    StatsView findFirstTokenStats(@Param("from") Instant from, @Param("to") Instant to);
    
    @Query(value = """
            select task_type as taskType,
                   count(*) as count,
                   avg(total_ms) as avgMs,
                   percentile_cont(0.5) within group (order by total_ms) as p50Ms,
                   percentile_cont(0.95) within group (order by total_ms) as p95Ms
            from llm_generations
            where status = 'SUCCESS' and total_ms is not null
              and created_at >= :from and created_at < :to
            group by task_type
            """, nativeQuery = true)
    List<TaskStatsView> findTaskStats(@Param("from") Instant from, @Param("to") Instant to);
}
