package com.telme.store.dto.req;

import com.telme.store.entity.StoreServiceType;

import io.github.resilience4j.core.lang.Nullable;

import java.util.Set;
import lombok.Builder;

// StoreSearchProperties로 default확인
@Builder
public record StoreNearbySearchRequest(
                Double latitude,
                Double longitude,
                Integer radiusMeters, // 검색반경(미터)
                Integer limit, // 표시할 매장갯수
                Set<StoreServiceType.Code> serviceTypes, // 업무종류 필터
                @Nullable Boolean openNow // 영업중 필터(설정으로 켜기전에는 무시됨)
) {
}
