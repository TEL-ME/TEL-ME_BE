-- 관리자 FAQ 목록이 FAQ별 인용 횟수를 message_sources.faq_id로 집계·정렬한다.
-- V1은 message_id 인덱스만 만들어 faq_id 조회는 전체 탐색으로 돈다.
-- 지금은 행이 적어 체감되지 않지만 질문 하나당 근거가 여러 줄 쌓여 목록을 열 때마다 느려진다.
CREATE INDEX idx_source_faq ON message_sources (faq_id);
