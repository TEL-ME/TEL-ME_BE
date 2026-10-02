-- nullable 컬럼만 추가한다. 추적 도입 전 실행은 값이 null이라 기록된 실행과 구분된다.
-- 기존 실행의 소유권·삭제 생명주기를 그대로 따르며, 모델 원문 출력은 저장하지 않는다.
ALTER TABLE chat_executions ADD COLUMN pipeline_trace jsonb;
ALTER TABLE llm_generations ADD COLUMN request_options jsonb;
