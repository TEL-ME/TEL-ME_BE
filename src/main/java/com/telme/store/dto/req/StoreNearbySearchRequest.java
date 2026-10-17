package com.telme.store.dto.req;

import com.telme.store.config.StoreSearchProperties;
import com.telme.store.entity.StoreServiceType;

import io.github.resilience4j.core.lang.Nullable;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.Builder;

// StoreSearchProperties로 default확인
@Builder
public record StoreNearbySearchRequest(
                @NotNull(message = "{store.latitude.required}")
                @DecimalMin(value = "-90", message = "{store.latitude.range}")
                @DecimalMax(value = "90", message = "{store.latitude.range}")
                Double latitude,
                @NotNull(message = "{store.longitude.required}")
                @DecimalMin(value = "-180", message = "{store.longitude.range}")
                @DecimalMax(value = "180", message = "{store.longitude.range}")
                Double longitude,
                @Min(value = 1, message = "{store.radius.min}")
                Integer radiusMeters, // 검색반경(미터)
                @Min(value = StoreSearchProperties.MIN_LIMIT, message = "{store.limit.min}")
                @Max(value = StoreSearchProperties.MAX_LIMIT, message = "{store.limit.max}")
                Integer limit, // 표시할 매장갯수
                Set<StoreServiceType.Code> serviceTypes, // 업무종류 필터
                @Nullable Boolean openNow // 영업중 필터(설정으로 켜기전에는 무시됨)
) {
}
