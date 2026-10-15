-- 입력 검사 영수증의 보존 기간과 무관하게 실제 별표 위치를 유지한다. 원문 번호는 저장하지 않는다.
ALTER TABLE chat_messages ADD COLUMN input_mask_spans jsonb;

ALTER TABLE chat_messages ADD CONSTRAINT ck_chat_messages_input_mask_spans_array
    CHECK (input_mask_spans IS NULL OR jsonb_typeof(input_mask_spans) = 'array');
