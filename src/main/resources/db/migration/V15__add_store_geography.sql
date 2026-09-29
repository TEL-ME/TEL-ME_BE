-- 최근접 매장 검색(KNN)이 쓰는 PostGIS 좌표 컬럼과 공간 인덱스.
CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE stores
    ADD COLUMN geog geography(Point, 4326)
        GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude::float8, latitude::float8), 4326)::geography) STORED;

-- 쿼리 WHERE에 status = 'OPEN'이 그대로 있어야 KNN적용 가능
CREATE INDEX idx_stores_geog_open ON stores USING gist (geog) WHERE status = 'OPEN';
