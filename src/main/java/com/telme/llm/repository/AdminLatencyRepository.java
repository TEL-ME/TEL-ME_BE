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
    
    // 서버가 잰 처리 시간(total_ms)만 센다. 측정값이 없는 과거 실행을 started_at·ended_at 차이로 채우면
    // 측정 방식이 다른 값이 섞이므로 넣지 않는다. 그래서 total_ms 도입 전 기간은 0건으로 나온다.
    @Query(value = """
            select count(*) as count,
                   avg(total_ms) as avgMs,
                   percentile_cont(0.5) within group (order by total_ms) as p50Ms,
                   percentile_cont(0.95) within group (order by total_ms) as p95Ms
            from chat_executions
            where status = 'COMPLETED' and output_message_id is not null and total_ms is not null
              and started_at >= :from and started_at < :to
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
