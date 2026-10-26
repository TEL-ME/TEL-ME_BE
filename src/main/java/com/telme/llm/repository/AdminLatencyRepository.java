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
    
    // total_ms가 있으면 우선하고, 과거 기록만 시작·종료 시각 차이로 계산한다. 음수·미완료 시간은 제외한다.
    @Query(value = """
            select count(*) as count,
                   avg(elapsed_ms) as avgMs,
                   percentile_cont(0.5) within group (order by elapsed_ms) as p50Ms,
                   percentile_cont(0.95) within group (order by elapsed_ms) as p95Ms
            from (
                select coalesce(total_ms, extract(epoch from (ended_at - started_at)) * 1000) as elapsed_ms
                from chat_executions
                where status = 'COMPLETED' and output_message_id is not null
                  and started_at >= :from and started_at < :to
            ) executions
            where elapsed_ms >= 0
            """, nativeQuery = true)
    StatsView findExecutionStats(@Param("from") Instant from, @Param("to") Instant to);
    
    // 단조 시계 전환 전 기록에는 시스템 시계 보정으로 생긴 음수가 있을 수 있다. 0ms는 유효한 측정값이다.
    @Query(value = """
            select count(*) as count,
                   avg(first_token_ms) as avgMs,
                   percentile_cont(0.5) within group (order by first_token_ms) as p50Ms,
                   percentile_cont(0.95) within group (order by first_token_ms) as p95Ms
            from llm_generations
            where status = 'SUCCESS' and first_token_ms >= 0
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
            where status = 'SUCCESS' and total_ms >= 0
              and created_at >= :from and created_at < :to
            group by task_type
            """, nativeQuery = true)
    List<TaskStatsView> findTaskStats(@Param("from") Instant from, @Param("to") Instant to);
}
