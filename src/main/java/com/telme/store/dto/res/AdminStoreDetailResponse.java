package com.telme.store.dto.res;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
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
        List<Hours> hours,
        List<AdminStoreServiceResponse> services,
        Instant createdAt,
        Instant updatedAt,
        Integer lockVersion
        ) {
    public record Hours(String dayOfWeek, LocalTime openTime, LocalTime closeTime, boolean closed) {
    }
}
