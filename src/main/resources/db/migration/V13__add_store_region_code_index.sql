-- 지역 검색(region_code LIKE 'prefix%')용. DB 콜레이션이 en_US.utf8이면 일반 btree는 LIKE 전방 일치에 쓰이지 않아
-- varchar_pattern_ops로 만든다. 검색 노출 대상이 OPEN 매장뿐이라 부분 인덱스로 둔다
CREATE INDEX idx_store_region_open ON stores (region_code varchar_pattern_ops) WHERE status = 'OPEN';
