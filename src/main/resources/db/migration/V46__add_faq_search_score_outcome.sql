-- 회색 구간 판정 뒤의 실제 결과를 검색 점수 기록에 남긴다. passed는 판정 전 기존 임계값 기준이라 실제 근거 여부와 다를 수 있다.
-- gray_zone_outcome은 FaqGrayZoneOutcome 이름이다(바로 통과, 판정기가 통과, 판정 실패 뒤 점수로 통과, 판정기가 거절,
-- 판정 실패로 버림, 아래 선 미만). 판정 실패를 판정기가 살리거나 거절한 결과와 나눠 Bedrock 장애 때 숫자가 부풀지 않게 한다.
-- 판정이 끝난 뒤 채운다. 이 칸을 넣기 전 기록과 판정 없이 끝난 검색(회색 구간 꺼짐, 후보 검색 등)은 NULL이다
ALTER TABLE faq_search_scores
    ADD COLUMN gray_zone_outcome VARCHAR(30);
