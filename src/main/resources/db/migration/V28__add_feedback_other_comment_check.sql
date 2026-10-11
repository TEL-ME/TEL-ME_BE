-- FeedbackModels.Input의 "기타(OTHER)면 의견 필수" 규칙을 DB에도 동일하게 둔다(V4와 같은 이유).
-- 애플리케이션은 공백만 있는 의견을 null로 바꾸므로, 우회 쓰기로 들어온 공백 문자열도 의견이 없는 것으로 본다.
-- reason_code가 NULL(좋아요)이면 앞 조건이 NULL이 되어 CHECK를 통과한다
ALTER TABLE message_feedback
    ADD CONSTRAINT ck_feedback_other_comment
        CHECK (reason_code <> 'OTHER' OR COALESCE(btrim(comment), '') <> '');