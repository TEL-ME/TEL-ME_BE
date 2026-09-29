package com.telme.store.converter;

import com.telme.store.dto.res.AdminStoreDetailResponse;
import com.telme.store.dto.res.AdminStoreListItemResponse;
import com.telme.store.dto.res.AdminStoreListResponse;
import com.telme.store.dto.res.AdminStoreServiceResponse;
import com.telme.store.entity.Store;
import com.telme.store.entity.StoreHours;
import com.telme.store.entity.StoreService;
import com.telme.store.entity.StoreServiceType;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class AdminStoreConverter {

    public AdminStoreListResponse toListResponse(Page<Long> storeIds, List<Store> stores) {
        List<AdminStoreListItemResponse> items = stores.stream().map(this::toListItem).toList();
        return new AdminStoreListResponse(
                items, storeIds.getNumber(), storeIds.getSize(),
                storeIds.getTotalElements(), storeIds.getTotalPages());
    }
    
    public AdminStoreDetailResponse toDetail(Store store) {
        return new AdminStoreDetailResponse(
                store.getStoreId(),
                store.getName(),
                store.getAddress(),
                store.getPhone(),
                store.getRegionCode(),
                store.getLatitude(),
                store.getLongitude(),
                store.getStatus().name(),
                toHours(store),
                toServices(store),
                store.getCreatedAt(),
                store.getUpdatedAt());
    }
    
    private AdminStoreListItemResponse toListItem(Store store) {
        return new AdminStoreListItemResponse(
                store.getStoreId(),
                store.getName(),
                store.getAddress(),
                store.getPhone(),
                toServices(store),
                store.getStatus().name(),
                store.getUpdatedAt());
    }
    
    // DB에 있는 요일만 반환한다. 빠진 요일을 휴무로 채우면 실제 휴무와 데이터 없음이 구분되지 않는다.
    // 7일 보장은 매장 등록·수정 API에서 검증한다.
    private List<AdminStoreDetailResponse.Hours> toHours(Store store) {
        return store.getHours().stream()
                .sorted(Comparator.comparing((StoreHours hours) -> hours.getId().getDayOfWeek()))
                .map(hours -> new AdminStoreDetailResponse.Hours(
                        DayOfWeek.of(hours.getId().getDayOfWeek()).name(),
                        hours.getOpenTime(),
                        hours.getCloseTime(),
                        hours.isClosed()))
                .toList();
    }
    
    // 조회할 때마다 업무 표시 순서가 바뀌지 않도록 id 순으로 고정한다
    private List<AdminStoreServiceResponse> toServices(Store store) {
        return store.getServices().stream()
                .map(StoreService::getServiceType)
                .sorted(Comparator.comparing(StoreServiceType::getServiceTypeId))
                .map(type -> new AdminStoreServiceResponse(type.getCode().name(), type.getName()))
                .toList();
    }
}
