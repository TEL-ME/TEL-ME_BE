-- 대시보드가 열릴 때마다 질문 수를 세는데 조건에 맞는 인덱스가 없어 테이블을 통째로 훑는다
CREATE INDEX ix_chat_messages_question_created_at
    ON chat_messages (created_at)
    WHERE role = 'USER' AND message_type = 'QUESTION';
