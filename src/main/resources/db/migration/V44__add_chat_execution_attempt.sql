-- 같은 질문을 처리한 횟수. 사용자가 다시 시도를 누르면 2가 된다.
ALTER TABLE chat_executions ADD COLUMN attempt SMALLINT NOT NULL DEFAULT 1;

-- 질문 하나당 다시 시도는 한 번만 허용한다. 동시에 눌러도 두 번째 실행은 만들어지지 않는다.
CREATE UNIQUE INDEX uk_chat_executions_retry ON chat_executions (input_message_id) WHERE attempt > 1;
