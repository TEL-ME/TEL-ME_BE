-- 관리자 오류 목록이 상태로 거르고 최신순으로 정렬하는데, 맞는 인덱스가 없어 매번 테이블 전체를 읽고 정렬한다.
-- llm_generations는 호출과 재시도마다 행이 늘고 오류는 그중 일부라 오류 행에만 인덱스를 둔다.
-- 같은 시각의 행이 있어 generation_id까지 넣어야 정렬을 인덱스 순서대로 끝낼 수 있다
CREATE INDEX ix_llm_generations_errors
    ON llm_generations (created_at DESC, generation_id DESC)
    WHERE status IN ('TIMEOUT', 'CONNECTION_FAILED', 'MODEL_ERROR');