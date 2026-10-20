-- 상담 종료 요약(V31) 기능을 없앤다. 요약 결과는 다른 곳에서 읽지 않고, 세션·실행 기록은 그대로 둔다
DROP TABLE IF EXISTS chat_session_end_summaries;
