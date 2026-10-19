-- 하위 질문(상담 요청)마다 고객에게 실제로 보인 결과. 상담 종료 요약이 답변 글자를 거꾸로 해석하지 않고 읽는다.
-- 답변 생성·라우팅에는 쓰지 않는다. 이 컬럼 이전 기록은 NULL이고 요약은 그때 예전 규칙을 쓴다.
-- answer_outcome: ANSWERED(근거 있는 안내) / STORES(매장 목록) / NO_EVIDENCE(답하지 못함) /
--                 ASKED(조건을 되물음, answer_text는 물은 질문) / SPLIT_REFUSED(나눠 보내 달라는 안내로 처리하지 않음)
-- answer_text: 이 하위 질문에 대해 고객에게 보인 문구(복합 답변이면 그 하위 질문 문단의 본문)
-- answer_message_id: answer_text가 담긴 메시지
-- display_question: 복합 답변 소제목으로 보인 하위 질문 문구. 단일 질문은 NULL(원문 메시지가 곧 질문이다)
ALTER TABLE consult_requests
    ADD COLUMN answer_outcome    VARCHAR(20)
        CHECK (answer_outcome IN ('ANSWERED', 'STORES', 'NO_EVIDENCE', 'ASKED', 'SPLIT_REFUSED')),
    ADD COLUMN answer_text       TEXT,
    ADD COLUMN answer_message_id BIGINT REFERENCES chat_messages (message_id),
    ADD COLUMN display_question  TEXT;
