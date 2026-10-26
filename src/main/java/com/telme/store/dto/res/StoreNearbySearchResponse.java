package com.telme.store.dto.res;

import java.util.List;
import lombok.Builder;

// 매장 배열 대신 객체로 감싸 나중에 필드를 더해도 응답을 읽는 쪽이 깨지지 않게 한다
@Builder
public record StoreNearbySearchResponse(
        List<StoreNearbyResponse> stores,
        int radiusMeters // 실제로 검색한 반경. 요청이 상한을 넘으면 상한으로 줄어든다
) {
}
