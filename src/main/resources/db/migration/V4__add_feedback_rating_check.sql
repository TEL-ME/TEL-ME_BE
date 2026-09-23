-- FeedbackModels.Input의 애플리케이션 검증 규칙을 DB에도 동일하게 둔다.
-- 배치·관리자 도구 등 우회 쓰기가 생겨도 잘못된 행이 만들어지지 않도록 한다.

-- LIKE에 남은 사유·의견은 비운다. 앱에서 LIKE로 수정할 때 하는 정규화와 동일하다.
UPDATE message_feedback SET reason_code = NULL WHERE rating = 'LIKE' AND reason_code IS NOT NULL;
UPDATE message_feedback SET comment = NULL WHERE rating = 'LIKE' AND comment IS NOT NULL;

-- DISLIKE인데 사유가 없는 행은 임의로 지우거나 채우지 않는다.
-- 그런 행이 남아 있으면 아래 제약 추가가 그 자리에서 실패해 배포가 막히므로,
-- 실패하면 데이터를 먼저 조사하고 팀 정책을 정한 뒤 처리한다.
ALTER TABLE message_feedback
    ADD CONSTRAINT ck_feedback_dislike_reason
        CHECK (
			(rating = 'DISLIKE' AND reason_code IS NOT NULL) OR
			(rating <> 'DISLIKE' AND reason_code IS NULL)
		),
    ADD CONSTRAINT ck_feedback_like_no_comment
        CHECK (rating = 'DISLIKE' OR comment IS NULL);