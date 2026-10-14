-- 미승계 게스트는 회원 기준 조회에 포함되지 않으므로 승계된 행만 인덱싱한다.
-- 한 회원에게 여러 게스트가 승계될 수 있어 유니크 제약은 두지 않는다.
CREATE INDEX idx_guest_merged_user
    ON guests (merged_user_id)
    WHERE merged_user_id IS NOT NULL;
