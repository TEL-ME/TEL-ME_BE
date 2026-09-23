-- FeedbackModels.Input의 애플리케이션 검증 규칙을 DB에도 동일하게 둔다.
-- 배치·관리자 도구 등 우회 쓰기가 생겨도 잘못된 행이 만들어지지 않도록 한다.

-- 기존 행을 자동으로 고치거나 제거하지 않는다
-- 위반 행이 남아 있으면 아래 제약 추가가 그 자리에서 실패해 배포가 막히므로,
-- 실패하면 데이터를 먼저 조사하고 팀 정책을 정한 뒤 처리한다.
ALTER TABLE message_feedback
    ADD CONSTRAINT ck_feedback_dislike_reason
        CHECK (
            (rating = 'DISLIKE' AND reason_code IS NOT NULL) OR
            (rating <> 'DISLIKE' AND reason_code IS NULL)
        ),
    ADD CONSTRAINT ck_feedback_like_no_comment
        CHECK (rating = 'DISLIKE' OR comment IS NULL);