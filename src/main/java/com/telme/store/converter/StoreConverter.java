package com.telme.store.converter;

import com.telme.store.dto.res.StoreNearbyResponse;
import com.telme.store.repository.StoreNearbyQueryRepository;
import org.springframework.stereotype.Component;

@Component
public class StoreConverter {

    public StoreNearbyResponse toNearbyResponse(StoreNearbyQueryRepository.Row row) {
        return StoreNearbyResponse.builder()
                .storeId(row.storeId())
                .name(row.name())
                .address(row.address())
                .phone(row.phone())
                .latitude(row.latitude())
                .longitude(row.longitude())
                // 화면 표시용이라 m 단위 정수로 충분하다. 정렬은 DB의 원래 실수 거리로 이미 끝났다
                .distanceMeters((int) Math.round(row.distanceMeters()))
                .build();
    }
}
