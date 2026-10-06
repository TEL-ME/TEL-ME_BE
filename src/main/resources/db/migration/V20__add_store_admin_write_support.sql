-- 업무 종류가 개발 시드(dev-migration V2)에만 있어 운영에서는 매장 등록·수정이 전부 실패하고
-- 등록 화면의 업무 체크박스도 비어 있다. 개발 DB는 시드가 먼저 넣으므로 같은 코드는 건너뛴다
INSERT INTO store_service_types (code, name) VALUES
    ('NEW_LINE', '신규가입'),
    ('PORT_IN', '번호이동'),
    ('NAME_CHANGE', '명의변경'),
    ('USIM_REISSUE', '유심재발급')
ON CONFLICT (code) DO NOTHING;

-- 두 관리자가 같은 매장을 저장하면 나중 저장이 영업시간·업무까지 통째로 덮어쓴다.
-- 영업시간·업무만 바뀌면 stores 행이 그대로라 @Version으로는 못 잡아, 수정할 때마다 touch가 올린다.
-- 기존 행과 잠금을 모르는 적재 경로를 위해 기본값을 준다
ALTER TABLE stores
    ADD COLUMN lock_version INT NOT NULL DEFAULT 0;