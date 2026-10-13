-- 사용자용 상담 종료 요약과 비동기 작업 상태를 내부 문맥 요약과 분리한다.
CREATE TABLE chat_session_end_summaries (
    session_id BIGINT PRIMARY KEY REFERENCES chat_sessions(session_id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    result JSONB,
    source_through_sequence_no INTEGER CHECK (source_through_sequence_no >= 0),
    source_execution_id BIGINT REFERENCES chat_executions(execution_id) ON DELETE SET NULL,
    prompt_version VARCHAR(30),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    claim_token UUID,
    lease_until TIMESTAMPTZ,
    failure_code VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    generated_at TIMESTAMPTZ,
    CONSTRAINT ck_end_summary_status CHECK (status IN ('PENDING', 'GENERATING', 'COMPLETED', 'EMPTY', 'FAILED')),
    CONSTRAINT ck_end_summary_result CHECK (
        (status = 'COMPLETED' AND result IS NOT NULL AND generated_at IS NOT NULL)
        OR (status <> 'COMPLETED' AND result IS NULL AND generated_at IS NULL)
    ),
    CONSTRAINT ck_end_summary_lease CHECK (
        (status = 'GENERATING' AND claim_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'GENERATING' AND claim_token IS NULL AND lease_until IS NULL)
    )
);
CREATE INDEX ix_end_summary_pending
    ON chat_session_end_summaries (created_at, session_id) WHERE status = 'PENDING';
CREATE INDEX ix_end_summary_lease
    ON chat_session_end_summaries (lease_until) WHERE status = 'GENERATING';
