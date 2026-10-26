package com.telme.chat.dto.res;

import java.math.BigDecimal;
import lombok.Builder;

/** latitude/longitude는 매장 좌표이며 지역 검색의 distanceMeters는 null이다. */
@Builder
public record ChatStoreResponse(
        Long storeId, String name, String address, String phone,
        BigDecimal latitude, BigDecimal longitude, Integer distanceMeters) {
}
