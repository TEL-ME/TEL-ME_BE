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

CREATE FUNCTION sync_store_tags() RETURNS TRIGGER
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        UPDATE stores SET tags = store_tags(NEW.store_id) WHERE store_id = NEW.store_id;
    END IF;
    IF TG_OP IN ('DELETE', 'UPDATE') THEN
        UPDATE stores SET tags = store_tags(OLD.store_id) WHERE store_id = OLD.store_id;
    END IF;
    RETURN NULL;
END
$$;

CREATE TRIGGER trg_store_services_tags
    AFTER INSERT OR UPDATE OR DELETE ON store_services
    FOR EACH ROW
EXECUTE FUNCTION sync_store_tags();

UPDATE stores s SET tags = store_tags(s.store_id)
WHERE EXISTS (SELECT 1 FROM store_services ss WHERE ss.store_id = s.store_id);

-- 영업 중 매장의 위치와 태그. 태그 조건은 인덱스 안에서 걸러진다(KNN)
CREATE INDEX idx_stores_geog_tags ON stores USING gist (geog, tags gist__int_ops) WHERE status = 'OPEN';
