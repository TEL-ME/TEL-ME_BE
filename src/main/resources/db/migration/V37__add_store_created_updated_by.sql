-- 관리자 매장 등록·수정·폐점을 누가 했는지 남긴다. FAQ(faqs.created_by/updated_by)와 같은 방식이다.
-- 이미 있는 매장과 시드·일괄 적재 매장은 기록이 없어 비워 둔다
ALTER TABLE stores
    ADD COLUMN created_by BIGINT REFERENCES users (user_id),
    ADD COLUMN updated_by BIGINT REFERENCES users (user_id);
