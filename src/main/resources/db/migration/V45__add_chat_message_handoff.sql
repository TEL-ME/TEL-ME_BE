-- FAQ에서 근거를 찾지 못한 답변이라 상담원 연결을 안내해야 하는 메시지
ALTER TABLE chat_messages ADD COLUMN handoff BOOLEAN NOT NULL DEFAULT FALSE;
