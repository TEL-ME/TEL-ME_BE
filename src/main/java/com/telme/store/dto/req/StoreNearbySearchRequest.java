package com.telme.store.dto.req;

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
                @NotNull(message = "위도를 입력해 주세요.")
                @DecimalMin(value = "-90", message = "위도는 -90에서 90 사이의 올바른 숫자여야 합니다.")
                @DecimalMax(value = "90", message = "위도는 -90에서 90 사이의 올바른 숫자여야 합니다.")
                Double latitude,
                @NotNull(message = "경도를 입력해 주세요.")
                @DecimalMin(value = "-180", message = "경도는 -180에서 180 사이의 올바른 숫자여야 합니다.")
                @DecimalMax(value = "180", message = "경도는 -180에서 180 사이의 올바른 숫자여야 합니다.")
                Double longitude,
                @Min(value = 1, message = "검색 반경은 1m 이상이어야 합니다.")
                Integer radiusMeters, // 검색반경(미터)
                @Min(value = 1, message = "매장 개수는 1 이상이어야 합니다.")
                @Max(value = MAX_LIMIT, message = "매장 개수는 {value} 이하여야 합니다.")
                Integer limit, // 표시할 매장갯수
                Set<StoreServiceType.Code> serviceTypes, // 업무종류 필터
                @Nullable Boolean openNow // 영업중 필터(설정으로 켜기전에는 무시됨)
) {
    // 매장 개수 상한. 반경(설정값)과 달리 API 계약(초과 시 400)이라 환경별로 바꾸지 않도록 상수로 둔다.
    // 요청 검증(@Max), 기본 개수 설정 검증, 서비스의 내부 호출 상한이 모두 이 값을 쓴다
    public static final int MAX_LIMIT = 20;
}
