-- PostgreSQL은 MySQL의 "ON UPDATE CURRENT_TIMESTAMP"가 없어서 updated_at 자동 갱신은
-- 트리거로 구현한다. updated_at 컬럼을 가진 모든 테이블이 이 함수를 공유한다.
CREATE FUNCTION set_updated_at() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
