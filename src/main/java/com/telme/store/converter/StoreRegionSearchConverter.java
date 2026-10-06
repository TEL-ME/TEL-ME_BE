package com.telme.store.converter;

import com.telme.store.dto.res.StoreRegionSearchResponse;
import com.telme.store.entity.Store;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoreRegionSearchConverter {

    private final StoreCommonConverter storeCommonConverter;

    public StoreRegionSearchResponse toResponse(Page<?> page, List<Store> stores) {
        return new StoreRegionSearchResponse(
                stores.stream().map(this::toStore).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private StoreRegionSearchResponse.Store toStore(Store store) {
        return new StoreRegionSearchResponse.Store(
                store.getStoreId(),
                store.getName(),
                store.getAddress(),
                store.getPhone(),
                store.getRegionCode(),
                store.getLatitude(),
                store.getLongitude(),
                storeCommonConverter.toServiceTypes(store.getServices())
        );
    }
}
