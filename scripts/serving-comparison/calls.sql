-- 측정 한 회차(load_chat.py 결과의 meta.startedAt부터 끝까지) 동안 앱이 남긴 LLM 호출을 서빙·작업·결과별로 센다.
-- 엑사원 호출당 시간과, 장애 시나리오에서 Bedrock 호출이 실제로 실패하고 엑사원으로 넘어갔는지 확인한다.
--   psql "$DATABASE_URL" -v since="'2026-10-20T10:00:00+09:00'" -v until="'2026-10-20T10:20:00+09:00'" -f calls.sql
SELECT coalesce(g.request_options ->> 'provider', 'ollama')                AS provider,
       g.task_type,
       g.status,
       count(*)                                                            AS calls,
       percentile_disc(0.5) WITHIN GROUP (ORDER BY g.total_ms)             AS p50_ms,
       percentile_disc(0.95) WITHIN GROUP (ORDER BY g.total_ms)            AS p95_ms,
       max(g.total_ms)                                                     AS max_ms
FROM llm_generations g
WHERE g.created_at >= :since::timestamptz
  AND g.created_at < :until::timestamptz
GROUP BY 1, 2, 3
ORDER BY 1, 2, 3;

-- 턴 단위 서버 처리 시간(앱이 잰 값). load_chat.py의 턴 시간과 차이가 크면 앱 대기열(동시 4턴)에서 기다린 것이다
SELECT e.status,
       count(*)                                                            AS turns,
       percentile_disc(0.5) WITHIN GROUP (ORDER BY e.total_ms)             AS p50_ms,
       percentile_disc(0.95) WITHIN GROUP (ORDER BY e.total_ms)            AS p95_ms,
       max(e.total_ms)                                                     AS max_ms
FROM chat_executions e
WHERE e.started_at >= :since::timestamptz
  AND e.started_at < :until::timestamptz
GROUP BY 1
ORDER BY 1;
