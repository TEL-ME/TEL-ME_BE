-- FAQ 내용이 바뀌어도 유지되는 식별자. faq_id는 환경마다 다르고 content_hash는 수정 시 바뀌어서
-- 평가셋 정답 매칭과 로더 중복 판단에 쓸 수 없다. 값은 원본 JSON의 slot_id(예: BILLING-0001)
-- 기존 행은 로더가 content_hash로 매칭해 채운다. 원본에 없는 FAQ(dev 시드, 관리자 생성)는 NULL
ALTER TABLE faqs
    ADD COLUMN slot_id VARCHAR(50);

ALTER TABLE faqs
    ADD CONSTRAINT uk_faqs_slot_id UNIQUE (slot_id);
