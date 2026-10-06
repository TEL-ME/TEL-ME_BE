package com.telme.store.dto.res;

import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record StoreNearbyResponse(
        Long storeId,
        String name,
        String address,
        String phone,
        BigDecimal latitude,
        BigDecimal longitude,
        int distanceMeters // 직선거리(T map경로와 다름)
) {
}
