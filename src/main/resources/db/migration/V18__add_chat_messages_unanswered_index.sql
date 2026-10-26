-- 근거를 못 찾은 답변(answer_basis)과 끝내지 못한 답변(status)은 서로 겹치지 않아 한쪽만으로는 누락된다.
-- 해당 행이 전체의 일부라 그 조건에만 인덱스를 둔다. 같은 시각이 겹칠 수 있어 message_id까지 넣는다.
CREATE INDEX ix_chat_messages_unanswered
    ON chat_messages (created_at DESC, message_id DESC)
    WHERE role = 'ASSISTANT'
      AND (answer_basis IN ('NO_EVIDENCE', 'OUT_OF_SCOPE')
           OR status IN ('FAILED', 'TIMEOUT'));
