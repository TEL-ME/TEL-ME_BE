package com.telme.store.dto.res;

import java.math.BigDecimal;
import java.util.List;

public record StoreRegionSearchResponse(
        List<Store> stores,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public record Store(
            Long storeId,
            String name,
            String address,
            String phone,
            String regionCode,
            BigDecimal latitude,
            BigDecimal longitude,
            List<ServiceType> services
    ) {
    }

    public record ServiceType(
            String code,
            String name
    ) {
    }
}
