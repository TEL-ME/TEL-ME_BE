package com.telme.store.converter;

import com.telme.store.dto.res.AdminStoreDetailResponse;
import com.telme.store.dto.res.AdminStoreListItemResponse;
import com.telme.store.dto.res.AdminStoreListResponse;
import com.telme.store.entity.Store;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminStoreConverter {

    private final StoreCommonConverter storeCommonConverter;

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
                storeCommonConverter.toHours(store.getHours()),
                storeCommonConverter.toServiceTypes(store.getServices()),
                store.getCreatedAt(),
                store.getUpdatedAt());
    }
    
    private AdminStoreListItemResponse toListItem(Store store) {
        return new AdminStoreListItemResponse(
                store.getStoreId(),
                store.getName(),
                store.getAddress(),
                store.getPhone(),
                storeCommonConverter.toServiceTypes(store.getServices()),
                store.getStatus().name(),
                store.getUpdatedAt());
    }
}
