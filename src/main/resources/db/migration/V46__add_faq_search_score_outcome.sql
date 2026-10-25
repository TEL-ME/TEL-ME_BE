-- 회색 구간 판정 뒤의 실제 결과를 검색 점수 기록에 남긴다. passed는 판정 전 기존 임계값 기준이라 실제 근거 여부와 다를 수 있다.
-- judged는 판정기가 이 검색의 후보를 하나라도 확인했는지, final_passed는 판정까지 거친 뒤 근거로 쓴 FAQ가 있었는지다.
-- passed_by_judge는 근거로 쓴 FAQ가 모두 판정을 거친 후보인지다. 판정 없이 통과한 FAQ가 하나라도 근거에 있으면 false라,
-- 판정기가 거절한 후보와 바로 통과한 후보가 섞인 검색을 판정 덕분에 찾은 검색으로 세지 않는다.
-- 세 값은 판정이 끝난 뒤 채운다. 이 칸을 넣기 전 기록과 판정 없이 끝난 검색(회색 구간 꺼짐, 후보 검색 등)은 NULL이다
ALTER TABLE faq_search_scores
    ADD COLUMN judged          BOOLEAN,
    ADD COLUMN final_passed    BOOLEAN,
    ADD COLUMN passed_by_judge BOOLEAN;
