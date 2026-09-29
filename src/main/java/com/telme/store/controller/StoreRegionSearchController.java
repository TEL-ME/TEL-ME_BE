package com.telme.store.controller;

import com.telme.global.common.CustomResponse;
import com.telme.store.dto.req.StoreRegionSearchRequest;
import com.telme.store.dto.res.StoreRegionSearchResponse;
import com.telme.store.service.StoreRegionSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Store", description = "매장 검색 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stores")
public class StoreRegionSearchController {

    private final StoreRegionSearchService storeRegionSearchService;

    @Operation(summary = "지역 코드로 매장 검색",
            description = "위치 권한이 없을 때 법정동코드 앞자리로 영업 중(OPEN) 매장을 찾는다. 폐업 매장은 제외한다.")
    @GetMapping
    public CustomResponse<StoreRegionSearchResponse> searchByRegion(
            @Valid @ParameterObject @ModelAttribute StoreRegionSearchRequest request) {
        return CustomResponse.onSuccess(storeRegionSearchService.search(request));
    }
}
