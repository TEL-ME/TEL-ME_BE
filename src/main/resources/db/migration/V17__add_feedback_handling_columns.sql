-- 관리자가 싫어요를 확인하고 조치했는지 표시할 자리가 없어, 같은 건을 매번 다시 보게 된다.
ALTER TABLE message_feedback
    ADD COLUMN handled_at   TIMESTAMPTZ,
    ADD COLUMN handled_by   BIGINT REFERENCES users (user_id),
    ADD COLUMN handled_note TEXT;

-- 처리 시각만 있고 처리자가 없으면 누가 봤는지 알 수 없어 둘을 함께 채우게 한다.
ALTER TABLE message_feedback
    ADD CONSTRAINT ck_feedback_handled_pair
        CHECK ((handled_at IS NULL) = (handled_by IS NULL)),
-- 좋아요는 조치할 대상이 아니라 관리자 목록에 올라오지 않는다.
    ADD CONSTRAINT ck_feedback_handled_dislike_only
        CHECK (handled_at IS NULL OR rating = 'DISLIKE');

-- 관리자 화면의 기본 목록이 미처리 싫어요를 최신순으로 보는 것이라 그 조건만 인덱스로 둔다.
CREATE INDEX ix_feedback_unhandled_dislike
    ON message_feedback (created_at DESC)
    WHERE rating = 'DISLIKE' AND handled_at IS NULL;
