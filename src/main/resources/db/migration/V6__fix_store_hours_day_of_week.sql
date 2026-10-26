-- store_hours.day_of_week를 1(월)~7(일)로 통일하고 CHECK로 고정
-- V1 주석은 1~7인데 V2 dev 시드가 0~6으로 넣어 두 규약이 섞여 있었다.
-- ISO-8601과 java.time.DayOfWeek가 1~7이라 그쪽으로 맞춘다. 운영 DB에는 해당 행이 없어 UPDATE는 0건
UPDATE store_hours SET day_of_week = 7 WHERE day_of_week = 0;

ALTER TABLE store_hours
    ADD CONSTRAINT ck_store_hours_day CHECK (day_of_week BETWEEN 1 AND 7);
