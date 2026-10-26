ALTER TABLE chat_sessions
    ADD COLUMN summary_through_sequence_no INT NOT NULL DEFAULT 0;

ALTER TABLE chat_sessions
    ADD CONSTRAINT ck_chat_session_summary_cursor
        CHECK (summary_through_sequence_no >= 0);

-- 개발용 V2 시드에 저장된 기존 요약은 현재 세션 이력 전체를 반영한 값으로 취급한다.
-- 주의: 운영 DB에 부분 이력만 반영한 기존 요약이 있다면, 이 백필 전에 해당 데이터를 확인해야 한다.
UPDATE chat_sessions AS session
SET summary_through_sequence_no = COALESCE((
    SELECT MAX(message.sequence_no)
    FROM chat_messages AS message
    WHERE message.session_id = session.session_id
), 0)
WHERE NULLIF(BTRIM(session.summary), '') IS NOT NULL
  AND session.summary_through_sequence_no = 0;
