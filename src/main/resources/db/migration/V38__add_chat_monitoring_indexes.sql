-- 모니터링 목록 조인용 인덱스
CREATE INDEX ix_chat_executions_input_message ON chat_executions (input_message_id);

CREATE INDEX ix_input_guard_event_message ON chat_input_guard_events (message_id)
    WHERE message_id IS NOT NULL;

-- 모니터링 상태 필터용 부분 인덱스(드문 상태만)
CREATE INDEX ix_chat_executions_not_completed
    ON chat_executions (status, input_message_id DESC) WHERE status <> 'COMPLETED';

CREATE INDEX ix_chat_messages_blocked
    ON chat_messages (message_id DESC) WHERE message_type = 'BLOCKED';
