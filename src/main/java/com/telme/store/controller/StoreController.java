package com.telme.store.controller;

import com.telme.global.common.CustomResponse;
import com.telme.store.dto.req.StoreNearbySearchRequest;
import com.telme.store.dto.req.StoreRouteRequest;
import com.telme.store.dto.res.StoreDetailResponse;
import com.telme.store.dto.res.StoreNearbySearchResponse;
import com.telme.store.dto.res.StoreRouteResponse;
import com.telme.store.dto.res.StoreServiceTypeListResponse;
import com.telme.store.service.StoreQueryService;
import com.telme.store.service.StoreRouteService;
import com.telme.store.service.StoreSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Store", description = "매장 검색 API")
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreSearchService storeSearchService;
    private final StoreQueryService storeQueryService;
    private final StoreRouteService storeRouteService;

    @Operation(
            summary = "가까운 매장 검색",
            description = "좌표에서 가까운 순으로 영업 중(폐점 제외) 매장을 반환합니다. 반경 기본값은 10km이고 10km를 넘으면 "
                    + "10km로 줄여 검색하며, 실제 검색한 반경을 radiusMeters로 돌려줍니다. 개수는 기본 5개이며, 최대 개수(20개)를 초과하면 400 에러를 반환합니다. "
                    + "serviceTypes를 반복해 여러 업무를 보내면 모두 가능한 매장만 반환합니다. 거리는 직선거리(m)입니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공. 결과가 없으면 stores가 빈 배열"),
        @ApiResponse(responseCode = "400", description = "STORE400-4: 영업 중 필터 미지원, COMMON400-1: 값 형식 오류 (좌표 없음·범위 밖, 반경 1m 미만, 개수 20 초과 등)"),
        @ApiResponse(responseCode = "503", description = "STORE503-0: 검색 지연")
    })
    @GetMapping("/nearby")
    public CustomResponse<StoreNearbySearchResponse> searchNearby(
            @Valid @ParameterObject @ModelAttribute StoreNearbySearchRequest request) {
        return CustomResponse.onSuccess(storeSearchService.findNearbyStores(request));
    }

    @Operation(
            summary = "매장 상세 조회",
            description = "기본 정보·좌표, 월요일부터의 요일별 영업시간, 가능 업무를 반환합니다. 폐점 매장은 404입니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "COMMON400-1: 매장 ID 형식 오류 또는 1 미만"),
        @ApiResponse(responseCode = "404", description = "STORE404-0: 매장이 없거나 폐점함")
    })
    @GetMapping("/{storeId}")
    public CustomResponse<StoreDetailResponse> getStore(
            @Parameter(description = "조회할 매장 ID") @PathVariable @Positive(message = "매장 ID는 1 이상이어야 합니다.") long storeId) {
        return CustomResponse.onSuccess(storeQueryService.getStore(storeId));
    }

    @Operation(
            summary = "매장까지 길찾기",
            description = "현재 위치(출발지)에서 매장까지의 경로를 이동 수단(CAR 자가용, TRANSIT 대중교통, WALK 도보, BICYCLE 자전거)별로 "
                    + "반환합니다. steps의 path를 순서대로 이어 지도에 그리면 됩니다. 좌표는 위도·경도 순서이며, 출발지 좌표는 "
                    + "경로 재사용을 위해 소수점 3자리(도보·자전거는 4자리)로 반올림해 조회하므로 경로의 시작점이 약간 다를 수 있습니다. "
                    + "출발지와 매장이 같은 위치면 거리 0에 steps가 빈 배열입니다. 경로는 수단에 따라 2~10분간 재사용합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "조회 성공"),
        @ApiResponse(responseCode = "400", description = "STORE400-5: 최대 이동 거리 초과, COMMON400-1: 요청값 형식 오류"),
        @ApiResponse(responseCode = "404", description = "STORE404-0: 매장이 없거나 폐점함, STORE404-1: 두 지점 사이의 경로를 찾지 못함"),
        @ApiResponse(responseCode = "503", description = "STORE503-2: 길찾기 서비스 연결 실패·호출 한도 초과 (FE는 카카오맵 링크로 대신한다)")
    })
    @GetMapping("/{storeId}/route")
    public CustomResponse<StoreRouteResponse> getRoute(
            @Parameter(description = "도착지 매장 ID") @PathVariable @Positive(message = "매장 ID는 1 이상이어야 합니다.") long storeId,
            @Valid @ParameterObject @ModelAttribute StoreRouteRequest request) {
        return CustomResponse.onSuccess(storeRouteService.getRoute(storeId, request));
    }

    @Operation(summary = "업무 종류 목록", description = "업무 선택 칩에 쓸 업무 코드와 이름을 반환합니다.")
    @GetMapping("/service-types")
    public CustomResponse<StoreServiceTypeListResponse> getServiceTypes() {
        return CustomResponse.onSuccess(storeQueryService.getServiceTypes());
    }
}
