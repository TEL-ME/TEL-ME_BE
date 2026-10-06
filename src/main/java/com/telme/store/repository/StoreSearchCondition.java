package com.telme.store.repository;

import java.util.Map;

// 최근접 검색에 붙이는 필터 한 개. 여러 조건은 AND로 묶인다.
// 조건은 매장 별칭 s의 컬럼(store_id, tags 등)만 참조하고, 그 밖의 정보는 store_id로 다른 테이블을 조회해 판단한다.
// 시간에 따라 바뀌는 조건(영업 중)도 같은 방식이다(OpenNowCondition)
public interface StoreSearchCondition {

    String toSql();

    Map<String, Object> parameters();

    // 이 조건만 만족하는 매장을 담은 공간 인덱스가 있는지. 모든 조건이 그렇다면 그 인덱스에서 KNN으로 가까운 곳만 꺼내면 되고,
    // 하나라도 아니면(예: 요청 시각마다 달라지는 영업 중 조건) 가까운 후보를 하나씩 확인하고, 모자라면 반경 안을 모두 확인한다
    default boolean backedBySpatialIndex() {
        return false;
    }
}
