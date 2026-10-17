-- 질문 하나를 서버가 처리한 시간(ms). 접수 요청이 컨트롤러에 들어온 때부터 답변·실패 결과를 실행에 기록할 때까지
-- 같은 서버의 단조 시계로 잰 값이다. started_at·ended_at은 처리 이력 조회용으로 그대로 둔다.
-- 이 컬럼 이전 기록과 시작 값을 모르는 실행(서버 재시작 뒤 타임아웃 정리 등)은 0이 아니라 NULL로 둔다
ALTER TABLE chat_executions
    ADD COLUMN total_ms INT CHECK (total_ms >= 0);
