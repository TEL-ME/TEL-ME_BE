package com.telme.store.converter;

import com.telme.store.dto.res.StoreRegionSearchResponse;
import com.telme.store.entity.Store;
import com.telme.store.entity.StoreService;
import com.telme.store.entity.StoreServiceType;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class StoreRegionSearchConverter {

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
                toServices(store.getServices())
        );
    }

    private List<StoreRegionSearchResponse.ServiceType> toServices(List<StoreService> services) {
        return services.stream()
                .map(StoreService::getServiceType)
                .sorted(Comparator.comparing(StoreServiceType::getServiceTypeId))
                .map(type -> new StoreRegionSearchResponse.ServiceType(type.getCode().name(), type.getName()))
                .toList();
    }
}
