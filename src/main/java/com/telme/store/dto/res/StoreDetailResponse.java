package com.telme.store.dto.res;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;

// 영업 상태(영업 중·영업 종료·오늘 휴무)는 영업 상태 판정 작업(TELME-101)에서 필드로 더한다
@Builder
public record StoreDetailResponse(
        Long storeId,
        String name,
        String address,
        String phone,
        BigDecimal latitude,
        BigDecimal longitude,
        List<StoreHoursResponse> hours,
        List<StoreServiceTypeResponse> services
) {
}
