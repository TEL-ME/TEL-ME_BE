-- FeedbackModels.Input의 "기타(OTHER)면 의견 필수" 규칙을 DB에도 동일하게 둔다(V4와 같은 이유).
-- 애플리케이션은 String.strip()으로 공백을 지운 뒤 비면 의견 없음(null)으로 보므로, DB도 같은 문자를 공백으로 본다.
-- btrim은 일반 스페이스만 지우고 [[:space:]]는 DB 로케일마다 범위가 달라, Java Character.isWhitespace가
-- 참인 문자를 그대로 나열한다: 탭·줄바꿈 등 제어 문자, 스페이스, 전각 공백 같은 유니코드 공백.
-- 줄바꿈 없는 공백(U+00A0, U+2007, U+202F)은 Java도 지우지 않아 의견으로 남는다.
-- FeedbackApiIntegrationTest.databaseRejectsOtherWithOnlyWhitespace가 두 판정이 같은지 확인한다.
-- reason_code가 NULL(좋아요)이면 앞 조건이 NULL이 되어 CHECK를 통과한다
ALTER TABLE message_feedback
    ADD CONSTRAINT ck_feedback_other_comment
        CHECK (reason_code <> 'OTHER'
            OR (comment IS NOT NULL
                AND comment !~ '^[\t\n\u000B\f\r\u001C-\u001F \u1680\u2000-\u2006\u2008-\u200A\u2028\u2029\u205F\u3000]*$'));