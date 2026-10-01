package com.telme.store.dto.res;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminStoreDetailResponse(
        Long storeId,
        String name,
        String address,
        String phone,
        String regionCode,
        BigDecimal latitude,
        BigDecimal longitude,
        String status,
        List<StoreHoursResponse> hours,
        List<StoreServiceTypeResponse> services,
        Instant createdAt,
        Instant updatedAt
        ) {
}
