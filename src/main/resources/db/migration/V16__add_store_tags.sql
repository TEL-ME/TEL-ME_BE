-- 정적 조건(업무 종류와 앞으로 더할 매장 속성)을 매장별 태그 배열 하나와 공간 인덱스 하나로 처리한다.
CREATE EXTENSION IF NOT EXISTS intarray;

-- 태그 번호는 StoreTag(Java)와 같아야 한다. 정적 조건을 더하면 새 번호를 붙이고, 이미 쓴 번호는 바꾸지 않는다.
-- intarray는 NULL이 든 배열을 거부하므로 빈 배열을 기본값으로 둔다
ALTER TABLE stores ADD COLUMN tags INT[] NOT NULL DEFAULT '{}';

-- store_services가(service목록) 생성될때마다 Store에 tag배열 추가
CREATE FUNCTION store_tags(target_store_id BIGINT) RETURNS INT[]
    LANGUAGE sql STABLE AS
$$
SELECT COALESCE(array_agg(DISTINCT t.tag ORDER BY t.tag), '{}')
FROM (SELECT CASE sst.code
                 WHEN 'NEW_LINE' THEN 1
                 WHEN 'PORT_IN' THEN 2
                 WHEN 'NAME_CHANGE' THEN 3
                 WHEN 'USIM_REISSUE' THEN 4
                 END AS tag
      FROM store_services ss
      JOIN store_service_types sst ON sst.service_type_id = ss.service_type_id
      WHERE ss.store_id = target_store_id) t
WHERE t.tag IS NOT NULL
$$;

-- 태그만 고칠 때는 매장 수정 시각(updated_at)을 바꾸지 않는다. 태그는 store_services에서 계산되는 값이라
-- 관리자가 매장 정보를 고친 시각과 섞이면 안 된다. ALTER TABLE ... DISABLE TRIGGER로 끄면 매장 삭제가 store_services를
-- CASCADE로 지울 때 "cannot ALTER TABLE stores because it is being used by active queries"로 삭제가 실패하고
-- 트랜잭션이 끝날 때까지 stores 쓰기 잠금이 남아서, 트랜잭션 안에서만 보이는 설정값을 태그 갱신 동안만 켠다
DROP TRIGGER trg_stores_updated_at ON stores;

CREATE TRIGGER trg_stores_updated_at
    BEFORE UPDATE ON stores
    FOR EACH ROW
    WHEN (current_setting('telme.skip_stores_updated_at', true) IS DISTINCT FROM 'on')
EXECUTE FUNCTION set_updated_at();

CREATE FUNCTION sync_store_tags() RETURNS TRIGGER
    LANGUAGE plpgsql AS
$$
BEGIN
    PERFORM set_config('telme.skip_stores_updated_at', 'on', true);
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        UPDATE stores SET tags = store_tags(NEW.store_id) WHERE store_id = NEW.store_id;
    END IF;
    IF TG_OP IN ('DELETE', 'UPDATE') THEN
        UPDATE stores SET tags = store_tags(OLD.store_id) WHERE store_id = OLD.store_id;
    END IF;
    PERFORM set_config('telme.skip_stores_updated_at', 'off', true);
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_store_services_tags
    AFTER INSERT OR UPDATE OR DELETE ON store_services
    FOR EACH ROW
EXECUTE FUNCTION sync_store_tags();

-- 기존 매장 태그 채우기도 태그만 고치는 갱신이라 수정 시각을 바꾸지 않는다
SELECT set_config('telme.skip_stores_updated_at', 'on', true);
UPDATE stores s SET tags = store_tags(s.store_id)
WHERE EXISTS (SELECT 1 FROM store_services ss WHERE ss.store_id = s.store_id);
SELECT set_config('telme.skip_stores_updated_at', 'off', true);

-- 영업 중 매장의 위치와 태그. 태그 조건은 인덱스 안에서 걸러진다(KNN)
CREATE INDEX idx_stores_geog_tags ON stores USING gist (geog, tags gist__int_ops) WHERE status = 'OPEN';
